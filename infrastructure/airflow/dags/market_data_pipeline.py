"""
NSE-MCX-Trends: Daily Market Data Pipeline (Batch)
===================================================
Runs at 06:00 IST (00:30 UTC) every trading day.
Fetches instruments, historical OHLCV, technicals,
fundamentals, and generates end-of-day signals.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta

import requests
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.models import Variable

log = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────────────────────
# Configuration (set via Airflow Variables or fall back to defaults)
# ──────────────────────────────────────────────────────────────────────────────
BACKEND_URL = Variable.get("BACKEND_URL", default_var="http://backend:8080")
API_TIMEOUT = int(Variable.get("API_TIMEOUT_SECONDS", default_var="120"))
ADMIN_TOKEN = Variable.get("ADMIN_API_TOKEN", default_var="")

HEADERS = {
    "Content-Type": "application/json",
    "Accept": "application/json",
    "Authorization": f"Bearer {ADMIN_TOKEN}" if ADMIN_TOKEN else "",
}

INSTRUMENT_GROUPS = ["NSE_EQ", "NSE_FO", "MCX_FUT", "MCX_OPT"]

# ──────────────────────────────────────────────────────────────────────────────
# Default arguments
# ──────────────────────────────────────────────────────────────────────────────
default_args = {
    "owner": "trading-ops",
    "depends_on_past": False,
    "email_on_failure": False,
    "email_on_retry": False,
    "retries": 3,
    "retry_delay": timedelta(minutes=5),
    "retry_exponential_backoff": True,
    "max_retry_delay": timedelta(minutes=30),
    "execution_timeout": timedelta(minutes=60),
}

# ──────────────────────────────────────────────────────────────────────────────
# Helper: POST with error handling
# ──────────────────────────────────────────────────────────────────────────────

def _post(endpoint: str, payload: dict | None = None, timeout: int = API_TIMEOUT) -> dict:
    url = f"{BACKEND_URL}{endpoint}"
    log.info("POST %s  payload=%s", url, json.dumps(payload or {}))
    response = requests.post(url, json=payload or {}, headers=HEADERS, timeout=timeout)
    response.raise_for_status()
    result = response.json() if response.content else {}
    log.info("Response %s: %s", response.status_code, json.dumps(result)[:500])
    return result


def _get(endpoint: str, params: dict | None = None, timeout: int = API_TIMEOUT) -> dict:
    url = f"{BACKEND_URL}{endpoint}"
    log.info("GET %s  params=%s", url, params)
    response = requests.get(url, params=params, headers=HEADERS, timeout=timeout)
    response.raise_for_status()
    result = response.json() if response.content else {}
    log.info("Response %s: %s", response.status_code, json.dumps(result)[:500])
    return result


# ──────────────────────────────────────────────────────────────────────────────
# Task functions
# ──────────────────────────────────────────────────────────────────────────────

def fetch_nse_instruments(**context) -> None:
    """
    Sync master instrument list from NSE and MCX into the database.
    Calls the admin endpoint which fetches from exchange APIs and upserts.
    """
    log.info("Starting instrument sync for all exchanges ...")
    result = _post("/api/admin/instruments/sync", payload={"exchanges": ["NSE", "MCX"]})
    synced_count = result.get("syncedCount", 0)
    log.info("Instrument sync complete. Records synced: %d", synced_count)
    context["task_instance"].xcom_push(key="synced_instruments", value=synced_count)


def fetch_historical_data(**context) -> None:
    """
    Refresh historical OHLCV data for each instrument group.
    Data is fetched from Yahoo Finance / NSE / MCX APIs.
    """
    total_refreshed = 0
    for group in INSTRUMENT_GROUPS:
        log.info("Refreshing historical market data for group: %s", group)
        try:
            result = _post(
                "/api/admin/market-data/refresh",
                payload={
                    "instrumentGroup": group,
                    "interval": "1D",
                    "lookbackDays": 365,
                },
                timeout=300,
            )
            count = result.get("refreshedCount", 0)
            total_refreshed += count
            log.info("Group %s: refreshed %d candles", group, count)
        except requests.HTTPError as exc:
            log.warning("Failed to refresh %s: %s - continuing", group, exc)

    log.info("Total historical records refreshed: %d", total_refreshed)
    context["task_instance"].xcom_push(key="historical_records", value=total_refreshed)


def calculate_technicals(**context) -> None:
    """
    Trigger server-side calculation of all technical indicators:
    RSI, MACD, Bollinger Bands, ATR, EMA/SMA crossovers, etc.
    """
    log.info("Triggering technical indicator calculation ...")
    result = _post(
        "/api/admin/technicals/calculate",
        payload={
            "indicators": [
                "RSI", "MACD", "BOLLINGER_BANDS", "ATR",
                "EMA_9", "EMA_21", "EMA_50", "EMA_200",
                "SMA_20", "SMA_50", "VWAP", "OBV",
                "SUPERTREND", "ADX", "STOCHASTIC",
            ],
            "timeframes": ["1D", "1W", "1M"],
        },
        timeout=300,
    )
    calculated = result.get("calculatedCount", 0)
    log.info("Technical indicators calculated: %d", calculated)
    context["task_instance"].xcom_push(key="technicals_calculated", value=calculated)


def fetch_fundamental_data(**context) -> None:
    """
    Refresh fundamental data: P/E, P/B, EPS, promoter holding,
    institutional FII/DII data, corporate actions.
    """
    log.info("Refreshing fundamental data ...")
    result = _post(
        "/api/admin/fundamentals/refresh",
        payload={
            "dataPoints": [
                "PE_RATIO", "PB_RATIO", "EPS", "BOOK_VALUE",
                "PROMOTER_HOLDING", "FII_HOLDING", "DII_HOLDING",
                "MARKET_CAP", "REVENUE", "NET_PROFIT",
                "DEBT_TO_EQUITY", "DIVIDEND_YIELD",
                "CORPORATE_ACTIONS", "QUARTERLY_RESULTS",
            ],
            "exchanges": ["NSE"],
        },
        timeout=300,
    )
    refreshed = result.get("refreshedCount", 0)
    log.info("Fundamental data refreshed for %d instruments", refreshed)
    context["task_instance"].xcom_push(key="fundamentals_refreshed", value=refreshed)


def generate_signals(**context) -> None:
    """
    Run the signal generation engine. Combines technical + fundamental
    analysis with AI (Ollama/llama3.2) to produce BUY/SELL/HOLD signals.
    """
    log.info("Generating trading signals ...")
    result = _post(
        "/api/signals/generate",
        payload={
            "mode": "END_OF_DAY",
            "exchanges": ["NSE", "MCX"],
            "minConfidenceScore": 0.65,
            "useAiEnhancement": True,
        },
        timeout=600,
    )
    generated = result.get("generatedCount", 0)
    buy_signals = result.get("buySignals", 0)
    sell_signals = result.get("sellSignals", 0)
    log.info(
        "Signals generated: %d total (BUY=%d, SELL=%d)",
        generated, buy_signals, sell_signals,
    )
    context["task_instance"].xcom_push(key="signals_generated", value=generated)
    context["task_instance"].xcom_push(key="buy_signals", value=buy_signals)
    context["task_instance"].xcom_push(key="sell_signals", value=sell_signals)


def send_daily_report(**context) -> None:
    """
    Aggregate run statistics from XCom and POST a daily summary
    to the notification endpoint, then log the report.
    """
    ti = context["task_instance"]
    run_date = context["ds"]

    synced_instruments = ti.xcom_pull(task_ids="fetch_nse_instruments", key="synced_instruments") or 0
    historical_records = ti.xcom_pull(task_ids="fetch_historical_data", key="historical_records") or 0
    technicals_calculated = ti.xcom_pull(task_ids="calculate_technicals", key="technicals_calculated") or 0
    fundamentals_refreshed = ti.xcom_pull(task_ids="fetch_fundamental_data", key="fundamentals_refreshed") or 0
    signals_generated = ti.xcom_pull(task_ids="generate_signals", key="signals_generated") or 0
    buy_signals = ti.xcom_pull(task_ids="generate_signals", key="buy_signals") or 0
    sell_signals = ti.xcom_pull(task_ids="generate_signals", key="sell_signals") or 0

    report = {
        "runDate": run_date,
        "pipelineType": "DAILY_BATCH",
        "summary": {
            "instrumentsSynced": synced_instruments,
            "historicalRecordsRefreshed": historical_records,
            "technicalsCalculated": technicals_calculated,
            "fundamentalsRefreshed": fundamentals_refreshed,
            "signalsGenerated": signals_generated,
            "buySignals": buy_signals,
            "sellSignals": sell_signals,
        },
        "status": "SUCCESS",
    }

    log.info("=" * 70)
    log.info("DAILY PIPELINE REPORT — %s", run_date)
    log.info("=" * 70)
    for key, value in report["summary"].items():
        log.info("  %-35s: %s", key, value)
    log.info("=" * 70)

    try:
        _post("/api/admin/reports/daily", payload=report, timeout=30)
    except Exception as exc:
        log.warning("Failed to POST daily report: %s (non-fatal)", exc)


# ──────────────────────────────────────────────────────────────────────────────
# DAG Definition
# ──────────────────────────────────────────────────────────────────────────────

with DAG(
    dag_id="nse_mcx_market_data_pipeline",
    description="Daily batch pipeline: instruments → OHLCV → technicals → fundamentals → signals → report",
    schedule_interval="30 0 * * 1-5",       # 00:30 UTC = 06:00 IST, Mon–Fri
    start_date=datetime(2024, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["nse", "mcx", "market-data", "daily", "production"],
    doc_md=__doc__,
) as dag:

    t_instruments = PythonOperator(
        task_id="fetch_nse_instruments",
        python_callable=fetch_nse_instruments,
        doc_md="Sync instrument master from NSE and MCX exchanges into DB.",
    )

    t_historical = PythonOperator(
        task_id="fetch_historical_data",
        python_callable=fetch_historical_data,
        doc_md="Refresh 365-day OHLCV history for NSE_EQ, NSE_FO, MCX_FUT, MCX_OPT.",
    )

    t_technicals = PythonOperator(
        task_id="calculate_technicals",
        python_callable=calculate_technicals,
        doc_md="Calculate RSI, MACD, Bollinger Bands, ATR, EMA/SMA, VWAP, etc.",
    )

    t_fundamentals = PythonOperator(
        task_id="fetch_fundamental_data",
        python_callable=fetch_fundamental_data,
        doc_md="Refresh P/E, P/B, EPS, institutional holding, corporate actions.",
    )

    t_signals = PythonOperator(
        task_id="generate_signals",
        python_callable=generate_signals,
        doc_md="Generate AI-enhanced BUY/SELL/HOLD signals for all instruments.",
    )

    t_report = PythonOperator(
        task_id="send_daily_report",
        python_callable=send_daily_report,
        trigger_rule="all_done",    # run even if upstream tasks partially failed
        doc_md="Aggregate stats from XCom and log/POST the daily summary report.",
    )

    # ── Task dependency chain ────────────────────────────────────────────────
    t_instruments >> t_historical >> t_technicals >> t_fundamentals >> t_signals >> t_report
