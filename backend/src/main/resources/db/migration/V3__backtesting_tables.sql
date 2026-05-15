-- V3__backtesting_tables.sql
-- Backtesting, paper trading, and forward testing tables

CREATE TYPE backtest_status AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED');
CREATE TYPE trade_direction AS ENUM ('LONG', 'SHORT');
CREATE TYPE exit_reason AS ENUM ('TARGET_HIT', 'SL_HIT', 'SIGNAL', 'EXPIRED', 'END_OF_PERIOD');
CREATE TYPE paper_trade_status AS ENUM ('OPEN', 'CLOSED', 'CANCELLED');

-- ─── Backtest Results ─────────────────────────────────────────────────────────

CREATE TABLE backtest_results (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    strategy_name       VARCHAR(100) NOT NULL,
    instrument_id       BIGINT REFERENCES instruments(id),
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    initial_capital     DECIMAL(18,2) NOT NULL,
    final_capital       DECIMAL(18,2),
    total_return        DECIMAL(10,4),
    annualized_return   DECIMAL(10,4),
    max_drawdown        DECIMAL(10,4),
    sharpe_ratio        DECIMAL(8,4),
    sortino_ratio       DECIMAL(8,4),
    win_rate            DECIMAL(8,4),
    total_trades        INTEGER DEFAULT 0,
    winning_trades      INTEGER DEFAULT 0,
    losing_trades       INTEGER DEFAULT 0,
    avg_win             DECIMAL(12,4),
    avg_loss            DECIMAL(12,4),
    profit_factor       DECIMAL(8,4),
    commission_paid     DECIMAL(12,2),
    slippage_cost       DECIMAL(12,2),
    parameters_json     TEXT,
    status              backtest_status NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_backtest_strategy   ON backtest_results(strategy_name);
CREATE INDEX idx_backtest_instrument ON backtest_results(instrument_id);
CREATE INDEX idx_backtest_status     ON backtest_results(status);
CREATE INDEX idx_backtest_created    ON backtest_results(created_at DESC);

-- ─── Backtest Trades ──────────────────────────────────────────────────────────

CREATE TABLE backtest_trades (
    id              BIGSERIAL PRIMARY KEY,
    backtest_id     BIGINT NOT NULL REFERENCES backtest_results(id) ON DELETE CASCADE,
    instrument_id   BIGINT NOT NULL REFERENCES instruments(id),
    entry_date      DATE NOT NULL,
    exit_date       DATE,
    entry_price     DECIMAL(18,4) NOT NULL,
    exit_price      DECIMAL(18,4),
    quantity        INTEGER NOT NULL,
    direction       trade_direction NOT NULL,
    pnl             DECIMAL(18,2),
    pnl_percent     DECIMAL(10,4),
    exit_reason     exit_reason,
    signal_type     signal_type,
    holding_days    INTEGER,
    commission      DECIMAL(10,2),
    slippage        DECIMAL(10,2)
);

CREATE INDEX idx_bt_trades_backtest    ON backtest_trades(backtest_id);
CREATE INDEX idx_bt_trades_instrument  ON backtest_trades(instrument_id);
CREATE INDEX idx_bt_trades_entry_date  ON backtest_trades(entry_date DESC);

-- ─── Paper Trades ─────────────────────────────────────────────────────────────

CREATE TABLE paper_trades (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(50) NOT NULL,
    instrument_id   BIGINT NOT NULL REFERENCES instruments(id),
    signal_id       BIGINT REFERENCES trade_signals(id),
    direction       trade_direction NOT NULL,
    quantity        INTEGER NOT NULL,
    entry_price     DECIMAL(18,4) NOT NULL,
    exit_price      DECIMAL(18,4),
    entry_time      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    exit_time       TIMESTAMPTZ,
    pnl             DECIMAL(18,2),
    pnl_percent     DECIMAL(10,4),
    commission      DECIMAL(10,2) DEFAULT 0,
    status          paper_trade_status NOT NULL DEFAULT 'OPEN',
    exit_reason     VARCHAR(50),
    stoploss        DECIMAL(18,4),
    target          DECIMAL(18,4),
    notes           TEXT
);

CREATE INDEX idx_paper_trades_session    ON paper_trades(session_id);
CREATE INDEX idx_paper_trades_status     ON paper_trades(status);
CREATE INDEX idx_paper_trades_instrument ON paper_trades(instrument_id);
CREATE INDEX idx_paper_trades_entry_time ON paper_trades(entry_time DESC);

-- ─── Forward Test Results ─────────────────────────────────────────────────────

CREATE TABLE forward_test_results (
    id                  BIGSERIAL PRIMARY KEY,
    signal_id           BIGINT REFERENCES trade_signals(id),
    instrument_id       BIGINT NOT NULL REFERENCES instruments(id),
    prediction_date     DATE NOT NULL,
    look_ahead_days     INTEGER NOT NULL,
    predicted_direction VARCHAR(10) NOT NULL,
    actual_direction    VARCHAR(10),
    predicted_target    DECIMAL(18,4),
    actual_price        DECIMAL(18,4),
    entry_price         DECIMAL(18,4),
    was_correct         BOOLEAN,
    confidence_score    DECIMAL(5,2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fwd_test_instrument      ON forward_test_results(instrument_id);
CREATE INDEX idx_fwd_test_prediction_date ON forward_test_results(prediction_date DESC);
CREATE INDEX idx_fwd_test_correct         ON forward_test_results(was_correct);
CREATE INDEX idx_fwd_test_signal          ON forward_test_results(signal_id);
