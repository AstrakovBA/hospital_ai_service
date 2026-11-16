package ru.medis.aiassistant.llm;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.medis.aiassistant.config.LlmProperties;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GigaChatAccessTokenProviderTest {

    @Test
    void obtainsAndCachesOauthTokenUsingConfiguredCredentialsAndScope() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties properties = new LlmProperties();
        properties.getGigachat().setOauthUrl("https://oauth.example.test/api/v2/oauth");
        properties.getGigachat().setCredentials("encoded-client-credentials");
        properties.getGigachat().setScope("GIGACHAT_API_PERS");

        server.expect(requestTo("https://oauth.example.test/api/v2/oauth"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic encoded-client-credentials"))
                .andExpect(header("RqUID", containsString("-")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("scope=GIGACHAT_API_PERS")))
                .andRespond(withSuccess("""
                        {"access_token":"test-access-token","expires_at":%d}
                        """.formatted(Instant.now().plusSeconds(3600).toEpochMilli()), MediaType.APPLICATION_JSON));

        GigaChatAccessTokenProvider tokenProvider =
                new GigaChatAccessTokenProvider(builder.build(), properties);

        assertThat(tokenProvider.getAccessToken()).isEqualTo("test-access-token");
        assertThat(tokenProvider.getAccessToken()).isEqualTo("test-access-token");
        server.verify();
    }

    @Test
    void usesClientIdAndSecretWhenEncodedCredentialsAreNotConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties properties = new LlmProperties();
        properties.getGigachat().setOauthUrl("https://oauth.example.test/api/v2/oauth");
        properties.getGigachat().setClientId("client-id");
        properties.getGigachat().setClientSecret("client-secret");

        server.expect(requestTo("https://oauth.example.test/api/v2/oauth"))
                .andExpect(header("Authorization", "Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ="))
                .andRespond(withSuccess("""
                        {"access_token":"test-access-token","expires_at":%d}
                        """.formatted(Instant.now().plusSeconds(3600).toEpochMilli()), MediaType.APPLICATION_JSON));

        GigaChatAccessTokenProvider tokenProvider =
                new GigaChatAccessTokenProvider(builder.build(), properties);

        assertThat(tokenProvider.getAccessToken()).isEqualTo("test-access-token");
        server.verify();
    }
}
