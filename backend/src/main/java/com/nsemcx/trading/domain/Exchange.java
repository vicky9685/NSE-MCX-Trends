package com.nsemcx.trading.domain;

public enum Exchange {
    NSE("National Stock Exchange of India", "Asia/Kolkata", "09:15", "15:30"),
    BSE("Bombay Stock Exchange", "Asia/Kolkata", "09:15", "15:30"),
    MCX("Multi Commodity Exchange of India", "Asia/Kolkata", "09:00", "23:30"),
    NCDEX("National Commodity & Derivatives Exchange", "Asia/Kolkata", "09:00", "17:00"),
    GLOBAL("Global markets including US, Europe, Asia", "UTC", "00:00", "23:59");

    private final String fullName;
    private final String timezone;
    private final String marketOpen;
    private final String marketClose;

    Exchange(String fullName, String timezone, String marketOpen, String marketClose) {
        this.fullName = fullName;
        this.timezone = timezone;
        this.marketOpen = marketOpen;
        this.marketClose = marketClose;
    }

    public String getFullName() { return fullName; }
    public String getTimezone() { return timezone; }
    public String getMarketOpen() { return marketOpen; }
    public String getMarketClose() { return marketClose; }
}
