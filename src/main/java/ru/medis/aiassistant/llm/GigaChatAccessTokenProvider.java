package ru.medis.aiassistant.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.medis.aiassistant.config.LlmProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class GigaChatAccessTokenProvider {

    private static final long EXPIRY_SKEW_SECONDS = 60;

    private final RestClient oauthRestClient;
    private final LlmProperties properties;
    private String cachedToken;
    private Instant tokenExpiresAt = Instant.MIN;

    public GigaChatAccessTokenProvider(
            @Qualifier("gigachatOAuthRestClient") RestClient oauthRestClient,
            LlmProperties properties) {
        this.oauthRestClient = oauthRestClient;
        this.properties = properties;
    }

    public synchronized String getAccessToken() {
        String configuredToken = properties.getGigachat().getAccessToken();
        if (configuredToken != null && !configuredToken.isBlank()) {
            return configuredToken;
        }

        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(EXPIRY_SKEW_SECONDS))) {
            return cachedToken;
        }

        LlmProperties.GigachatProperties gigachat = properties.getGigachat();
        String credentials = resolveCredentials(gigachat);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("scope", gigachat.getScope());

        TokenResponse response = oauthRestClient.post()
                .uri(gigachat.getOauthUrl())
                .header("Authorization", "Basic " + credentials)
                .header("RqUID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("GigaChat OAuth response did not contain an access token");
        }
        if (response.expiresAt() <= System.currentTimeMillis()) {
            throw new IllegalStateException("GigaChat OAuth response contained an expired access token");
        }

        cachedToken = response.accessToken();
        tokenExpiresAt = Instant.ofEpochMilli(response.expiresAt());
        return cachedToken;
    }

    private String resolveCredentials(LlmProperties.GigachatProperties gigachat) {
        if (gigachat.getCredentials() != null && !gigachat.getCredentials().isBlank()) {
            return gigachat.getCredentials();
        }
        if (gigachat.getClientId() != null && !gigachat.getClientId().isBlank()
                && gigachat.getClientSecret() != null && !gigachat.getClientSecret().isBlank()) {
            String pair = gigachat.getClientId() + ":" + gigachat.getClientSecret();
            return Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("GigaChat credentials are not configured");
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_at") long expiresAt) {}
}
