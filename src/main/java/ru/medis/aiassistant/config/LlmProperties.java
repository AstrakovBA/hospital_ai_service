package ru.medis.aiassistant.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private static final Logger log = LoggerFactory.getLogger(LlmProperties.class);

    private String activeProvider = "gigachat";
    private String fallbackProvider = "internal";
    private GigachatProperties gigachat = new GigachatProperties();
    private InternalProperties internal = new InternalProperties();

    /**
     * Проверка конфигурации на старте. Секреты читаются только из переменных окружения,
     * их значения в логи не выводятся.
     */
    @PostConstruct
    void validate() {
        if (activeProvider == null || activeProvider.isBlank()) {
            throw new IllegalStateException("llm.active-provider must be configured");
        }
        if ("gigachat".equals(activeProvider)
                && !gigachat.hasAuthentication()) {
            log.warn("GigaChat selected as active provider but no GigaChat credentials are configured — "
                    + "requests will fail and the gateway will fall back to '{}'", fallbackProvider);
        }
    }

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
        private String oauthUrl;
        private String credentials;
        private String scope = "GIGACHAT_API_PERS";
        private String clientId;
        private String clientSecret;
        private String accessToken; // генерируется через OAuth
        private String model = "GigaChat-3-Ultra";
        private Duration timeout = Duration.ofSeconds(10);
        private int retries = 2;

        // getters/setters...
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getOauthUrl() { return oauthUrl; }
        public void setOauthUrl(String oauthUrl) { this.oauthUrl = oauthUrl; }
        public String getCredentials() { return credentials; }
        public void setCredentials(String credentials) { this.credentials = credentials; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public boolean hasAuthentication() {
            return isConfigured(accessToken)
                    || isConfigured(credentials)
                    || (isConfigured(clientId) && isConfigured(clientSecret));
        }
        private static boolean isConfigured(String value) {
            return value != null && !value.isBlank();
        }
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