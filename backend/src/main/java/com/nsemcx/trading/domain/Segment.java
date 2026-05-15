package com.nsemcx.trading.domain;

public enum Segment {
    EQUITY("Equity cash market for NSE/BSE listed stocks"),
    FUTURES("Futures contracts on indices and stocks"),
    OPTIONS("Options contracts - calls and puts"),
    COMMODITY("Commodity futures on MCX/NCDEX"),
    CURRENCY("Currency derivatives - USDINR, EURINR etc"),
    INDEX("Market indices like Nifty 50, Bank Nifty");

    private final String description;

    Segment(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
