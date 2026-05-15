package com.nsemcx.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker API credentials and endpoints.
 * Bound from application.yml prefix "brokers".
 *
 * Sensitive fields (apiKey, apiSecret) should be supplied via environment variables
 * rather than committed configuration files:
 *   ZERODHA_API_KEY, ZERODHA_API_SECRET
 *   ALICEBLUE_API_KEY, ALICEBLUE_USER_ID
 *   BONANZA_API_KEY
 */
@ConfigurationProperties(prefix = "brokers")
public class BrokerProperties {

    private Zerodha zerodha = new Zerodha();
    private AliceBlue aliceblue = new AliceBlue();
    private Bonanza bonanza = new Bonanza();

    // ------------------------------------------------------------------ getters / setters

    public Zerodha getZerodha() { return zerodha; }
    public void setZerodha(Zerodha zerodha) { this.zerodha = zerodha; }

    public AliceBlue getAliceblue() { return aliceblue; }
    public void setAliceblue(AliceBlue aliceblue) { this.aliceblue = aliceblue; }

    public Bonanza getBonanza() { return bonanza; }
    public void setBonanza(Bonanza bonanza) { this.bonanza = bonanza; }

    // ------------------------------------------------------------------ nested classes

    public static class Zerodha {
        private boolean enabled = false;
        private String apiKey = "";
        private String apiSecret = "";
        private String baseUrl = "https://api.kite.trade";
        private String userId = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    public static class AliceBlue {
        private boolean enabled = false;
        private String apiKey = "";
        private String apiSecret = "";
        private String baseUrl = "https://ant.aliceblueonline.com/rest/AliceBlueAPIService/api";
        private String userId = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    public static class Bonanza {
        private boolean enabled = false;
        private String apiKey = "";
        private String apiSecret = "";
        private String baseUrl = "https://api.bonanzaonline.com";
        private String userId = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
}
