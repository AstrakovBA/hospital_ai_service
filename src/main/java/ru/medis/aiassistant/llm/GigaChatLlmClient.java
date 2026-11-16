package ru.medis.aiassistant.llm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.medis.aiassistant.config.LlmProperties;
import ru.medis.aiassistant.exception.LlmUnavailableException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Реализация LlmClient для GigaChat API (SberCloud).
 *
 * GigaChat API:
 *   POST https://gigachat.devices.sberbank.ru/api/v1/chat/completions
 *   Authorization: Bearer {token}
 *   X-Session-ID: {uuid}
 */
@Component
public class GigaChatLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GigaChatLlmClient.class);
    private static final String PROVIDER_NAME = "gigachat";

    private final RestClient restClient;
    private final LlmProperties properties;
    private final Timer requestTimer;

    public GigaChatLlmClient(RestClient gigachatRestClient,
                             LlmProperties properties,
                             MeterRegistry meterRegistry) {
        this.restClient = gigachatRestClient;
        this.properties = properties;
        this.requestTimer = Timer.builder("llm.request.duration")
                .tag("provider", PROVIDER_NAME)
                .description("Время запроса к LLM")
                .register(meterRegistry);
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        long startTime = System.currentTimeMillis();
        String sessionId = UUID.randomUUID().toString();

        log.info("LLM request initiated | provider={} | sessionId={} | maxTokens={} | temperature={}",
                PROVIDER_NAME, sessionId, request.maxTokens(), request.temperature());

        // НЕ логируем содержимое prompt — может содержать персональные данные пациента
        log.debug("LLM request metadata: {}", request.metadata());

        try {
            GigaChatResponse chatResponse = requestTimer.record(() -> {
                // Формирование тела запроса в формате GigaChat API
                Map<String, Object> body = buildGigaChatRequestBody(request);

                return restClient.post()
                        .uri("/api/v1/chat/completions")
                        .header("Authorization", "Bearer " + properties.getGigachat().getAccessToken())
                        .header("X-Session-ID", sessionId)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(GigaChatResponse.class);
            });

            long latency = System.currentTimeMillis() - startTime;
            String text = extractText(chatResponse);

            log.info("LLM response received | provider={} | sessionId={} | latency={}ms | textLength={}",
                    PROVIDER_NAME, sessionId, latency, text != null ? text.length() : 0);

            return LlmResponse.of(text, PROVIDER_NAME, latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("LLM request failed | provider={} | sessionId={} | latency={}ms | error={}",
                    PROVIDER_NAME, sessionId, latency, e.getMessage());
            throw new LlmUnavailableException("GigaChat API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            // Lightweight health check
            restClient.get()
                    .uri("/api/v1/models")
                    .header("Authorization", "Bearer " + properties.getGigachat().getAccessToken())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("GigaChat health check failed: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildGigaChatRequestBody(LlmRequest request) {
        return Map.of(
                "model", properties.getGigachat().getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())
                ),
                "temperature", request.temperature(),
                "max_tokens", request.maxTokens(),
                "stream", false
        );
    }

    private String extractText(GigaChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return "";
        }
        return response.choices().get(0).message().content();
    }

    // Внутренние DTO для GigaChat API
    record GigaChatResponse(List<Choice> choices) {}
    record Choice(Message message) {}
    record Message(String role, String content) {}
}