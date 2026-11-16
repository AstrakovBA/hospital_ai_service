package ru.medis.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String activeProvider = "gigachat";
    private String fallbackProvider = "internal";
    private GigachatProperties gigachat = new GigachatProperties();
    private InternalProperties internal = new InternalProperties();

    // getters/setters...

    public String getActiveProvider() { return activeProvider; }
    public void setActiveProvider(String activeProvider) { this.activeProvider = activeProvider; }
    public String getFallbackProvider() { return fallbackProvider; }
    public void setFallbackProvider(String fallbackProvider) { this.fallbackProvider = fallbackProvider; }
    public GigachatProperties getGigachat() { return gigachat; }
    public void setGigachat(GigachatProperties gigachat) { this.gigachat = gigachat; }
    public InternalProperties getInternal() { return internal; }
    public void setInternal(InternalProperties internal) { this.internal = internal; }

    public static class GigachatProperties {
        private String baseUrl;
        private String clientId;
        private String clientSecret;
        private String accessToken; // генерируется через OAuth
        private String model = "GigaChat";
        private Duration timeout = Duration.ofSeconds(10);
        private int retries = 2;

        // getters/setters...
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }
    }

    public static class InternalProperties {
        private Duration timeout = Duration.ofSeconds(5);

        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }
}