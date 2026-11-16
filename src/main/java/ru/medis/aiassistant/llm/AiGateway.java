package ru.medis.aiassistant.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.medis.aiassistant.config.LlmProperties;
import ru.medis.aiassistant.exception.LlmUnavailableException;

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
            LlmResponse response = client.generate(request);
            recordSuccess(activeProvider);
            resetCircuitBreaker();
            return response;

        } catch (LlmUnavailableException e) {
            recordFailure(activeProvider);
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
        return properties.getActiveProvider();
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
            return LlmResponse.fallback(
                    "Сервис временно недоступен. Ваш вопрос зафиксирован, ответ будет предоставлен в ближайшее время.",
                    failedProvider
            );
        }

        try {
            log.info("Fallback execution | provider={}", fallbackProvider);
            LlmResponse response = fallbackClient.generate(request);
            recordSuccess(fallbackProvider);
            activateCircuitBreaker();
            return response;

        } catch (Exception fallbackEx) {
            recordFailure(fallbackProvider);
            log.error("Fallback also failed | provider={} | error={}", fallbackProvider, fallbackEx.getMessage());
            return LlmResponse.fallback(
                    "Сервис временно недоступен. Пожалуйста, обратитесь в регистратуру по телефону.",
                    fallbackProvider
            );
        }
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

    private void recordSuccess(String provider) {
        Counter.builder("llm.requests.total")
                .tag("provider", provider)
                .tag("status", "success")
                .register(meterRegistry)
                .increment();
    }

    private void recordFailure(String provider) {
        Counter.builder("llm.requests.total")
                .tag("provider", provider)
                .tag("status", "error")
                .register(meterRegistry)
                .increment();
    }
}