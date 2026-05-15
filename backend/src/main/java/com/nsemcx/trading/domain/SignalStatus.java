package com.nsemcx.trading.domain;

public enum SignalStatus {
    ACTIVE("Signal is active and waiting for entry trigger"),
    TRIGGERED("Entry trigger hit, position initiated"),
    TARGET_HIT("Target price reached, trade closed with profit"),
    STOPLOSS_HIT("Stop loss triggered, trade closed with defined loss"),
    EXPIRED("Signal expired without being triggered"),
    CANCELLED("Signal manually cancelled by user or system");

    private final String description;

    SignalStatus(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }

    public boolean isTerminal() {
        return this == TARGET_HIT || this == STOPLOSS_HIT
                || this == EXPIRED || this == CANCELLED;
    }

    public boolean isProfit() { return this == TARGET_HIT; }
    public boolean isLoss() { return this == STOPLOSS_HIT; }
}
