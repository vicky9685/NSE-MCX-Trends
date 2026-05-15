package com.nsemcx.trading.domain;

public enum SignalType {
    STRONG_BUY("Strong Buy - High conviction long entry with multiple confirmations", 1),
    BUY("Buy - Moderate conviction long entry", 2),
    WEAK_BUY("Weak Buy - Cautious long entry, small position size recommended", 3),
    NEUTRAL("Neutral - No directional bias, stay on sidelines", 0),
    WEAK_SELL("Weak Sell - Cautious short entry or exit longs", -3),
    SELL("Sell - Moderate conviction short entry", -2),
    STRONG_SELL("Strong Sell - High conviction short entry with multiple confirmations", -1),
    CALL_BUY("Call Buy - Buy call options for bullish directional play", 4),
    PUT_BUY("Put Buy - Buy put options for bearish directional play", -4),
    STRADDLE("Straddle - Buy both call and put for high volatility play", 5),
    STRANGLE("Strangle - OTM straddle for very high volatility play", 6),
    IRON_CONDOR("Iron Condor - Sell OTM call and put spreads for range-bound market", 7);

    private final String description;
    private final int directionBias; // positive=bullish, negative=bearish, 0=neutral

    SignalType(String description, int directionBias) {
        this.description = description;
        this.directionBias = directionBias;
    }

    public String getDescription() { return description; }
    public int getDirectionBias() { return directionBias; }

    public boolean isBullish() { return directionBias > 0; }
    public boolean isBearish() { return directionBias < 0; }
    public boolean isNeutral() { return directionBias == 0; }
    public boolean isOptionsStrategy() {
        return this == CALL_BUY || this == PUT_BUY || this == STRADDLE
                || this == STRANGLE || this == IRON_CONDOR;
    }
}
