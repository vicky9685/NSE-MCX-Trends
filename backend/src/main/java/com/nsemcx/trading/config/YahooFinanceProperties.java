package com.nsemcx.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Yahoo Finance HTTP client settings.
 * Bound from application.yml prefix "yahoo.finance".
 */
@ConfigurationProperties(prefix = "yahoo.finance")
public class YahooFinanceProperties {

    private String baseUrl = "https://query1.finance.yahoo.com";
    private String v8Url  = "https://query1.finance.yahoo.com/v8/finance/chart";
    private String v10Url = "https://query1.finance.yahoo.com/v10/finance/quoteSummary";

    /** Minimum delay between consecutive requests to avoid rate-limiting (ms). */
    private long rateLimitDelayMs = 1000;

    /** Number of retries before giving up on a failed request. */
    private int maxRetries = 3;

    /** HTTP read/connect timeout in seconds. */
    private int timeoutSeconds = 30;

    // ------------------------------------------------------------------ getters / setters

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getV8Url() { return v8Url; }
    public void setV8Url(String v8Url) { this.v8Url = v8Url; }

    public String getV10Url() { return v10Url; }
    public void setV10Url(String v10Url) { this.v10Url = v10Url; }

    public long getRateLimitDelayMs() { return rateLimitDelayMs; }
    public void setRateLimitDelayMs(long rateLimitDelayMs) { this.rateLimitDelayMs = rateLimitDelayMs; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
