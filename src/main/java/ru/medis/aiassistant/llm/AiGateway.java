package ru.medis.aiassistant.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.medis.aiassistant.config.LlmProperties;
import ru.medis.aiassistant.exception.LlmUnavailableException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Gateway — единая точка доступа к LLM-провайдерам.
 *
 * Задачи:
 * 1. Маршрутизация запросов к активному провайдеру
 * 2. Fallback при ошибке основного провайдера
 * 3. Метрики и логирование (без чувствительных данных)
 * 4. Изоляция бизнес-логики от конкретного провайдера (анти-vendor-lock)
 */
@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final Map<String, LlmClient> clients;
    private final LlmProperties properties;
    private final MeterRegistry meterRegistry;

    // Circuit breaker state
    private volatile boolean primaryFailed = false;
    private volatile long primaryFailureTimestamp = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 30_000; // 30 сек

    public AiGateway(List<LlmClient> clientList,
                     LlmProperties properties,
                     MeterRegistry meterRegistry) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(LlmClient::providerName, Function.identity()));
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        log.info("AiGateway initialized | providers={} | active={}",
                clients.keySet(), properties.getActiveProvider());
    }

    /**
     * Генерация ответа через AI Gateway.
     * При ошибке основного провайдера — автоматический fallback на внутренний.
     */
    public LlmResponse generate(LlmRequest request) {
        String activeProvider = resolveActiveProvider();
        LlmClient client = clients.get(activeProvider);

        if (client == null) {
            log.error("Unknown LLM provider: {}", activeProvider);
            throw new IllegalStateException("Unknown LLM provider: " + activeProvider);
        }

        log.info("AI Gateway routing | provider={} | requestMaxTokens={}", activeProvider, request.maxTokens());

        try {
            long start = System.nanoTime();
            LlmResponse raw = client.generate(request);
            recordLatency(activeProvider, System.nanoTime() - start);
            recordOutcome(activeProvider, "success");
            resetCircuitBreaker();
            return raw;

        } catch (LlmUnavailableException e) {
            recordOutcome(activeProvider, "error");
            log.warn("Primary provider failed, attempting fallback | failed={} | error={}",
                    activeProvider, e.getMessage());

            return executeFallback(request, activeProvider, e);
        }
    }

    /**
     * Определение активного провайдера с учётом Circuit Breaker.
     */
    private String resolveActiveProvider() {
        if (primaryFailed && isCircuitBreakerActive()) {
            log.info("Circuit breaker active: routing to fallback provider");
            return properties.getFallbackProvider();
        }
        String active = properties.getActiveProvider();
        // Если основной провайдер помечен недоступным, но таймаут блокатора истёк —
        // сбрасываем состояние и пробуем снова (half-open).
        if (primaryFailed) {
            resetCircuitBreaker();
        }
        return active;
    }

    private boolean isCircuitBreakerActive() {
        return (System.currentTimeMillis() - primaryFailureTimestamp) < CIRCUIT_BREAKER_TIMEOUT_MS;
    }

    /**
     * Fallback: переключение на резервного провайдера.
     */
    private LlmResponse executeFallback(LlmRequest request, String failedProvider, Exception cause) {
        String fallbackProvider = properties.getFallbackProvider();
        LlmClient fallbackClient = clients.get(fallbackProvider);

        if (fallbackClient == null || fallbackProvider.equals(failedProvider)) {
            log.error("No fallback provider available | failed={}", failedProvider);
            activateCircuitBreaker();
            return degradedResponse(failedProvider);
        }

        try {
            log.info("Fallback execution | provider={}", fallbackProvider);
            long start = System.nanoTime();
            LlmResponse response = fallbackClient.generate(request);
            recordLatency(fallbackProvider, System.nanoTime() - start);
            recordOutcome(fallbackProvider, "success_fallback");
            activateCircuitBreaker();
            return markAsFallback(response);

        } catch (Exception fallbackEx) {
            recordOutcome(fallbackProvider, "error");
            log.error("Fallback also failed | provider={} | error={}", fallbackProvider, fallbackEx.getMessage());
            activateCircuitBreaker();
            return degradedResponse(fallbackProvider);
        }
    }

    /** Безопасное сообщение при полной недоступности LLM (без ПД, без диагнозов). */
    private LlmResponse degradedResponse(String provider) {
        return LlmResponse.fallback(
                "Сервис временно недоступен. Ваш вопрос зафиксирован, "
                        + "пожалуйста, обратитесь в регистратуру по телефону.",
                provider
        );
    }

    /** Помечает ответ как полученный через fallback-провайдера. */
    private LlmResponse markAsFallback(LlmResponse response) {
        return new LlmResponse(response.text(), response.provider(), true, response.latencyMs());
    }

    private void activateCircuitBreaker() {
        primaryFailed = true;
        primaryFailureTimestamp = System.currentTimeMillis();
        log.warn("Circuit breaker activated for {} ms", CIRCUIT_BREAKER_TIMEOUT_MS);
    }

    private void resetCircuitBreaker() {
        if (primaryFailed) {
            primaryFailed = false;
            log.info("Circuit breaker reset — primary provider restored");
        }
    }

    private void recordOutcome(String provider, String status) {
        Counter.builder("llm.requests.total")
                .tag("provider", provider)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    private void recordLatency(String provider, long nanos) {
        io.micrometer.core.instrument.Timer.builder("llm.request.duration")
                .tag("provider", provider)
                .description("Длительность обращения к LLM-провайдеру")
                .register(meterRegistry)
                .record(Duration.ofNanos(nanos));
    }
}