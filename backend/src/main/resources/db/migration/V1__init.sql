-- V1__init.sql: Initial schema for NSE/MCX Trading System

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enums
CREATE TYPE segment_type AS ENUM ('EQUITY', 'FUTURES', 'OPTIONS', 'COMMODITY', 'CURRENCY', 'INDEX');
CREATE TYPE exchange_type AS ENUM ('NSE', 'BSE', 'MCX', 'NCDEX', 'GLOBAL');
CREATE TYPE signal_type AS ENUM (
    'STRONG_BUY', 'BUY', 'WEAK_BUY',
    'NEUTRAL',
    'WEAK_SELL', 'SELL', 'STRONG_SELL',
    'CALL_BUY', 'PUT_BUY', 'STRADDLE', 'STRANGLE', 'IRON_CONDOR'
);
CREATE TYPE signal_status AS ENUM ('ACTIVE', 'TRIGGERED', 'TARGET_HIT', 'STOPLOSS_HIT', 'EXPIRED', 'CANCELLED');
CREATE TYPE alert_type AS ENUM ('PRICE', 'VOLUME', 'RSI', 'MACD', 'BREAKOUT', 'SIGNAL', 'RISK', 'OI');
CREATE TYPE alert_severity AS ENUM ('INFO', 'WARNING', 'CRITICAL');

-- Instruments table
CREATE TABLE instruments (
    id                BIGSERIAL PRIMARY KEY,
    symbol            VARCHAR(50)   NOT NULL UNIQUE,
    name              VARCHAR(200)  NOT NULL,
    segment           segment_type  NOT NULL,
    exchange          exchange_type NOT NULL,
    lot_size          INTEGER       NOT NULL DEFAULT 1,
    tick_size         DECIMAL(10,4) NOT NULL DEFAULT 0.05,
    expiry_date       DATE,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    isin              VARCHAR(20),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_instruments_symbol ON instruments(symbol);
CREATE INDEX idx_instruments_exchange ON instruments(exchange);
CREATE INDEX idx_instruments_segment ON instruments(segment);

-- Market data table (OHLCV)
CREATE TABLE market_data (
    id            BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT        NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    timestamp     TIMESTAMPTZ   NOT NULL,
    open          DECIMAL(18,4) NOT NULL,
    high          DECIMAL(18,4) NOT NULL,
    low           DECIMAL(18,4) NOT NULL,
    close         DECIMAL(18,4) NOT NULL,
    volume        BIGINT        NOT NULL DEFAULT 0,
    open_interest BIGINT        DEFAULT 0,
    adj_close     DECIMAL(18,4),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_market_data_instrument_ts ON market_data(instrument_id, timestamp);
CREATE INDEX idx_market_data_timestamp ON market_data(timestamp DESC);
CREATE INDEX idx_market_data_instrument ON market_data(instrument_id);

-- Technical indicators table
CREATE TABLE technical_indicators (
    id            BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT        NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    timestamp     TIMESTAMPTZ   NOT NULL,
    rsi_14        DECIMAL(8,4),
    macd          DECIMAL(12,4),
    macd_signal   DECIMAL(12,4),
    macd_hist     DECIMAL(12,4),
    ema_20        DECIMAL(18,4),
    ema_50        DECIMAL(18,4),
    ema_200       DECIMAL(18,4),
    vwap          DECIMAL(18,4),
    bb_upper      DECIMAL(18,4),
    bb_middle     DECIMAL(18,4),
    bb_lower      DECIMAL(18,4),
    volume_ratio  DECIMAL(8,4),
    adx           DECIMAL(8,4),
    atr           DECIMAL(12,4),
    stoch_k       DECIMAL(8,4),
    stoch_d       DECIMAL(8,4),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_tech_ind_instrument_ts ON technical_indicators(instrument_id, timestamp);
CREATE INDEX idx_tech_ind_timestamp ON technical_indicators(timestamp DESC);

-- Fundamental data table
CREATE TABLE fundamental_data (
    id                BIGSERIAL PRIMARY KEY,
    instrument_id     BIGINT        NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    revenue_growth    DECIMAL(10,4),
    profit_growth     DECIMAL(10,4),
    debt_equity       DECIMAL(10,4),
    roe               DECIMAL(10,4),
    roce              DECIMAL(10,4),
    promoter_holding  DECIMAL(10,4),
    pe_ratio          DECIMAL(12,4),
    pb_ratio          DECIMAL(10,4),
    ev_ebitda         DECIMAL(10,4),
    fii_flow          DECIMAL(18,2),
    dii_flow          DECIMAL(18,2),
    market_cap        DECIMAL(20,2),
    dividend_yield    DECIMAL(8,4),
    eps               DECIMAL(12,4),
    book_value        DECIMAL(12,4),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_fund_data_instrument ON fundamental_data(instrument_id);

-- Trade signals table
CREATE TABLE trade_signals (
    id                BIGSERIAL PRIMARY KEY,
    instrument_id     BIGINT        NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    signal_type       signal_type   NOT NULL,
    confidence        DECIMAL(5,2)  NOT NULL CHECK (confidence BETWEEN 0 AND 100),
    entry_price       DECIMAL(18,4) NOT NULL,
    target1           DECIMAL(18,4),
    target2           DECIMAL(18,4),
    target3           DECIMAL(18,4),
    stoploss          DECIMAL(18,4),
    risk_reward       DECIMAL(8,4),
    time_horizon      VARCHAR(50),
    reasoning         TEXT,
    hedging_strategy  TEXT,
    strike_price      DECIMAL(18,4),
    probability       DECIMAL(5,2),
    fundamental_score DECIMAL(5,2),
    technical_score   DECIMAL(5,2),
    macro_score       DECIMAL(5,2),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ,
    triggered_at      TIMESTAMPTZ,
    status            signal_status NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_signals_instrument ON trade_signals(instrument_id);
CREATE INDEX idx_signals_status ON trade_signals(status);
CREATE INDEX idx_signals_created_at ON trade_signals(created_at DESC);
CREATE INDEX idx_signals_type ON trade_signals(signal_type);

-- Option chain table
CREATE TABLE option_chain (
    id            BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT        NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    expiry        DATE          NOT NULL,
    strike        DECIMAL(18,2) NOT NULL,
    ce_oi         BIGINT        DEFAULT 0,
    pe_oi         BIGINT        DEFAULT 0,
    ce_change_oi  BIGINT        DEFAULT 0,
    pe_change_oi  BIGINT        DEFAULT 0,
    ce_iv         DECIMAL(8,4),
    pe_iv         DECIMAL(8,4),
    ce_ltp        DECIMAL(12,4),
    pe_ltp        DECIMAL(12,4),
    ce_volume     BIGINT        DEFAULT 0,
    pe_volume     BIGINT        DEFAULT 0,
    pcr           DECIMAL(8,4),
    max_pain      DECIMAL(18,2),
    timestamp     TIMESTAMPTZ   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_option_chain_instrument ON option_chain(instrument_id);
CREATE INDEX idx_option_chain_expiry ON option_chain(expiry);
CREATE INDEX idx_option_chain_timestamp ON option_chain(timestamp DESC);
CREATE UNIQUE INDEX idx_option_chain_unique ON option_chain(instrument_id, expiry, strike, timestamp);

-- Portfolio positions table
CREATE TABLE portfolio_positions (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT        NOT NULL REFERENCES instruments(id),
    signal_id       BIGINT        REFERENCES trade_signals(id),
    broker          VARCHAR(50)   NOT NULL DEFAULT 'PAPER',
    position_type   VARCHAR(10)   NOT NULL CHECK (position_type IN ('LONG', 'SHORT')),
    quantity        INTEGER       NOT NULL,
    entry_price     DECIMAL(18,4) NOT NULL,
    current_price   DECIMAL(18,4),
    pnl             DECIMAL(18,2),
    pnl_percent     DECIMAL(8,4),
    stoploss        DECIMAL(18,4),
    target          DECIMAL(18,4),
    opened_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ,
    is_open         BOOLEAN       NOT NULL DEFAULT TRUE,
    notes           TEXT
);

CREATE INDEX idx_positions_instrument ON portfolio_positions(instrument_id);
CREATE INDEX idx_positions_is_open ON portfolio_positions(is_open);

-- Alerts table
CREATE TABLE alerts (
    id            BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT        REFERENCES instruments(id),
    signal_id     BIGINT        REFERENCES trade_signals(id),
    alert_type    alert_type    NOT NULL,
    severity      alert_severity NOT NULL DEFAULT 'INFO',
    title         VARCHAR(200)  NOT NULL,
    message       TEXT          NOT NULL,
    condition_val DECIMAL(18,4),
    current_val   DECIMAL(18,4),
    is_triggered  BOOLEAN       NOT NULL DEFAULT FALSE,
    triggered_at  TIMESTAMPTZ,
    is_read       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alerts_instrument ON alerts(instrument_id);
CREATE INDEX idx_alerts_is_triggered ON alerts(is_triggered);
CREATE INDEX idx_alerts_created_at ON alerts(created_at DESC);
CREATE INDEX idx_alerts_severity ON alerts(severity);

-- Market data embeddings for pgvector similarity search
CREATE TABLE market_data_embeddings (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT        NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    timestamp       TIMESTAMPTZ   NOT NULL,
    embedding       vector(1536)  NOT NULL,
    content_summary TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_embeddings_instrument ON market_data_embeddings(instrument_id);
CREATE INDEX idx_embeddings_timestamp ON market_data_embeddings(timestamp DESC);
-- IVFFlat index for approximate nearest neighbor search
CREATE INDEX idx_embeddings_vector ON market_data_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Seed default instruments
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
    ('RELIANCE', 'Reliance Industries Ltd', 'EQUITY', 'NSE', 1, 0.05),
    ('TCS', 'Tata Consultancy Services Ltd', 'EQUITY', 'NSE', 1, 0.05),
    ('HDFCBANK', 'HDFC Bank Ltd', 'EQUITY', 'NSE', 1, 0.05),
    ('INFY', 'Infosys Ltd', 'EQUITY', 'NSE', 1, 0.05),
    ('ICICIBANK', 'ICICI Bank Ltd', 'EQUITY', 'NSE', 1, 0.05),
    ('SBIN', 'State Bank of India', 'EQUITY', 'NSE', 1, 0.05),
    ('NIFTY50', 'Nifty 50 Index', 'INDEX', 'NSE', 50, 0.05),
    ('BANKNIFTY', 'Bank Nifty Index', 'INDEX', 'NSE', 15, 0.05),
    ('GOLD', 'Gold Futures MCX', 'COMMODITY', 'MCX', 1, 1.00),
    ('SILVER', 'Silver Futures MCX', 'COMMODITY', 'MCX', 30, 1.00),
    ('CRUDEOIL', 'Crude Oil Futures MCX', 'COMMODITY', 'MCX', 100, 1.00),
    ('NATURALGAS', 'Natural Gas Futures MCX', 'COMMODITY', 'MCX', 1250, 0.10),
    ('COPPER', 'Copper Futures MCX', 'COMMODITY', 'MCX', 2500, 0.05);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_instruments_updated_at
    BEFORE UPDATE ON instruments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
