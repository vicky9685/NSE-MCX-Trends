"""
NSE-MCX-Trends: Intraday Market Data Pipeline
==============================================
Runs every 15 minutes during IST market hours:
  Monday–Friday, 09:15–15:30 IST (03:45–10:00 UTC).

Tasks: fetch live quotes → update technicals → check signals → process alerts.
Uses SimpleHttpOperator for lightweight API calls.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta

from airflow import DAG
from airflow.models import Variable
from airflow.operators.python import PythonOperator, BranchPythonOperator
from airflow.operators.http import SimpleHttpOperator
from airflow.operators.dummy import DummyOperator
from airflow.utils.trigger_rule import TriggerRule
import pendulum

log = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────────────────────
BACKEND_HOST = Variable.get("BACKEND_HOST", default_var="backend")
BACKEND_PORT = Variable.get("BACKEND_PORT", default_var="8080")
ADMIN_TOKEN = Variable.get("ADMIN_API_TOKEN", default_var="")

IST = pendulum.timezone("Asia/Kolkata")
MARKET_OPEN_HOUR = 9
MARKET_OPEN_MINUTE = 15
MARKET_CLOSE_HOUR = 15
MARKET_CLOSE_MINUTE = 30

# ──────────────────────────────────────────────────────────────────────────────
# Default arguments
# ──────────────────────────────────────────────────────────────────────────────
default_args = {
    "owner": "trading-ops",
    "depends_on_past": False,
    "email_on_failure": False,
    "email_on_retry": False,
    "retries": 2,
    "retry_delay": timedelta(minutes=2),
    "retry_exponential_backoff": False,
    "execution_timeout": timedelta(minutes=10),
}

# ──────────────────────────────────────────────────────────────────────────────
# Market hours guard
# ──────────────────────────────────────────────────────────────────────────────

def is_market_open(**context) -> str:
    """
    Branch operator: returns task_id to execute next.
    Skips pipeline if outside NSE/MCX trading hours (IST) or on weekends.
    """
    now_ist = datetime.now(tz=IST)
    weekday = now_ist.weekday()           # 0=Mon ... 4=Fri, 5=Sat, 6=Sun
    hour = now_ist.hour
    minute = now_ist.minute

    if weekday >= 5:
        log.info("Weekend (%s) — skipping intraday pipeline", now_ist.strftime("%A"))
        return "market_closed"

    market_open_minutes = MARKET_OPEN_HOUR * 60 + MARKET_OPEN_MINUTE
    market_close_minutes = MARKET_CLOSE_HOUR * 60 + MARKET_CLOSE_MINUTE
    current_minutes = hour * 60 + minute

    if market_open_minutes <= current_minutes <= market_close_minutes:
        log.info("Market is OPEN at %s IST — proceeding", now_ist.strftime("%H:%M"))
        return "fetch_live_quotes"
    else:
        log.info("Market is CLOSED at %s IST — skipping", now_ist.strftime("%H:%M"))
        return "market_closed"


# ──────────────────────────────────────────────────────────────────────────────
# Auth header helper (evaluated at runtime)
# ──────────────────────────────────────────────────────────────────────────────

def get_auth_headers() -> dict:
    token = Variable.get("ADMIN_API_TOKEN", default_var="")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


# ──────────────────────────────────────────────────────────────────────────────
# Post-task logging callbacks
# ──────────────────────────────────────────────────────────────────────────────

def log_quotes_response(response, **context) -> None:
    log.info("Live quotes fetch response: status=%s body_len=%d",
             response.status_code, len(response.content))


def log_technicals_response(response, **context) -> None:
    log.info("Intraday technicals response: status=%s", response.status_code)


def log_signals_response(response, **context) -> None:
    log.info("Signal check response: status=%s", response.status_code)


def log_alerts_response(response, **context) -> None:
    log.info("Alert processing response: status=%s", response.status_code)


# ──────────────────────────────────────────────────────────────────────────────
# DAG Definition
# ──────────────────────────────────────────────────────────────────────────────

with DAG(
    dag_id="nse_mcx_intraday_pipeline",
    description="Intraday 15-min pipeline: live quotes → technicals → signals → alerts",
    # Runs every 15 minutes Mon-Fri. The BranchOperator guards against
    # off-hours execution so no resources are wasted outside market hours.
    schedule_interval="*/15 3-10 * * 1-5",   # 03:45–10:00 UTC ≈ 09:15–15:30 IST
    start_date=datetime(2024, 1, 1),
    catchup=False,
    max_active_runs=1,
    concurrency=1,
    default_args=default_args,
    tags=["nse", "mcx", "intraday", "live", "production"],
    doc_md=__doc__,
) as dag:

    # ── Guard: check if market is open ───────────────────────────────────────
    branch_market_check = BranchPythonOperator(
        task_id="check_market_hours",
        python_callable=is_market_open,
        doc_md="Skip pipeline outside NSE/MCX trading hours (09:15–15:30 IST, Mon–Fri).",
    )

    market_closed = DummyOperator(
        task_id="market_closed",
        doc_md="No-op: market is currently closed.",
    )

    # ── Task 1: Fetch live quotes ─────────────────────────────────────────────
    fetch_live_quotes = SimpleHttpOperator(
        task_id="fetch_live_quotes",
        method="POST",
        http_conn_id="backend_api",
        endpoint="/api/admin/market-data/live",
        data='{"exchanges": ["NSE", "MCX"], "includeOptionChain": true}',
        headers=get_auth_headers(),
        response_check=lambda response: response.status_code in (200, 202),
        log_response=True,
        extra_options={"timeout": 60},
        doc_md="Fetch real-time quotes for all active NSE and MCX instruments.",
    )

    # ── Task 2: Update intraday technicals ───────────────────────────────────
    update_technicals = SimpleHttpOperator(
        task_id="update_technicals",
        method="POST",
        http_conn_id="backend_api",
        endpoint="/api/admin/technicals/intraday",
        data='{"timeframes": ["1m", "5m", "15m"], "indicators": ["RSI", "MACD", "VWAP", "SUPERTREND", "ATR"]}',
        headers=get_auth_headers(),
        response_check=lambda response: response.status_code in (200, 202),
        log_response=True,
        extra_options={"timeout": 60},
        doc_md="Recalculate intraday technical indicators on 1m/5m/15m timeframes.",
    )

    # ── Task 3: Check and generate intraday signals ──────────────────────────
    check_signals = SimpleHttpOperator(
        task_id="check_signals",
        method="POST",
        http_conn_id="backend_api",
        endpoint="/api/signals/generate",
        data='{"mode": "INTRADAY", "exchanges": ["NSE", "MCX"], "minConfidenceScore": 0.70, "useAiEnhancement": false}',
        headers=get_auth_headers(),
        response_check=lambda response: response.status_code in (200, 202),
        log_response=True,
        extra_options={"timeout": 90},
        doc_md="Run intraday signal engine. AI enhancement disabled for speed.",
    )

    # ── Task 4: Process alerts ───────────────────────────────────────────────
    process_alerts = SimpleHttpOperator(
        task_id="process_alerts",
        method="POST",
        http_conn_id="backend_api",
        endpoint="/api/alerts/process",
        data='{"channels": ["WEBSOCKET", "TELEGRAM"], "priority": ["HIGH", "CRITICAL"]}',
        headers=get_auth_headers(),
        response_check=lambda response: response.status_code in (200, 202),
        log_response=True,
        extra_options={"timeout": 30},
        doc_md="Dispatch pending high/critical alerts via WebSocket and Telegram.",
    )

    # ── End marker ───────────────────────────────────────────────────────────
    pipeline_complete = DummyOperator(
        task_id="pipeline_complete",
        trigger_rule=TriggerRule.ONE_SUCCESS,
        doc_md="Marks successful completion of this intraday run.",
    )

    # ── Task dependency graph ─────────────────────────────────────────────────
    #
    #   check_market_hours
    #         ├── market_closed ──────────────────────────┐
    #         └── fetch_live_quotes                        │
    #                  └── update_technicals               │
    #                           └── check_signals          │
    #                                    └── process_alerts│
    #                                             └── pipeline_complete ←─┘
    #
    branch_market_check >> [market_closed, fetch_live_quotes]
    fetch_live_quotes >> update_technicals >> check_signals >> process_alerts >> pipeline_complete
    market_closed >> pipeline_complete
