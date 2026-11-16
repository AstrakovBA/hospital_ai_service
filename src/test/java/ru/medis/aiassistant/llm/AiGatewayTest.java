package ru.medis.aiassistant.llm;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.medis.aiassistant.config.LlmProperties;
import ru.medis.aiassistant.exception.LlmUnavailableException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiGateway — unit-тесты")
class AiGatewayTest {

    @Mock
    private LlmClient gigachatClient;

    @Mock
    private LlmClient internalClient;

    private AiGateway aiGateway;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setActiveProvider("gigachat");
        properties.setFallbackProvider("internal");

        when(gigachatClient.providerName()).thenReturn("gigachat");
        when(internalClient.providerName()).thenReturn("internal");

        aiGateway = new AiGateway(
                List.of(gigachatClient, internalClient),
                properties,
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("Успешная маршрутизация к активному провайдеру")
    void shouldRouteToActiveProvider() {
        LlmRequest request = LlmRequest.builder()
                .userPrompt("test")
                .build();
        LlmResponse expected = LlmResponse.of("response", "gigachat", 100);
        when(gigachatClient.generate(any())).thenReturn(expected);

        LlmResponse result = aiGateway.generate(request);

        assertThat(result.text()).isEqualTo("response");
        assertThat(result.provider()).isEqualTo("gigachat");
        verify(gigachatClient).generate(request);
        verify(internalClient, never()).generate(any());
    }

    @Test
    @DisplayName("Fallback при ошибке основного провайдера")
    void shouldFallbackOnPrimaryFailure() {
        LlmRequest request = LlmRequest.builder().userPrompt("test").build();
        when(gigachatClient.generate(any()))
                .thenThrow(new LlmUnavailableException("timeout"));
        when(internalClient.generate(any()))
                .thenReturn(LlmResponse.of("fallback answer", "internal", 10));

        LlmResponse result = aiGateway.generate(request);

        assertThat(result.text()).isEqualTo("fallback answer");
        assertThat(result.provider()).isEqualTo("internal");
    }

    @Test
    @DisplayName("Безопасный ответ при отказе обоих провайдеров")
    void shouldReturnSafeMessageWhenAllFail() {
        LlmRequest request = LlmRequest.builder().userPrompt("test").build();
        when(gigachatClient.generate(any()))
                .thenThrow(new LlmUnavailableException("down"));
        when(internalClient.generate(any()))
                .thenThrow(new RuntimeException("also down"));

        LlmResponse result = aiGateway.generate(request);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.text()).contains("временно недоступен");
    }
}