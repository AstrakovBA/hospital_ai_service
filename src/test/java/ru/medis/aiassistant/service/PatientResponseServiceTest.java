package ru.medis.aiassistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.medis.aiassistant.dto.PatientAnswerResponse;
import ru.medis.aiassistant.dto.PatientQuestionRequest;
import ru.medis.aiassistant.llm.AiGateway;
import ru.medis.aiassistant.llm.LlmRequest;
import ru.medis.aiassistant.llm.LlmResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientResponseService — unit-тесты")
class PatientResponseServiceTest {

    @Mock
    private AiGateway aiGateway;

    private PatientResponseService service;

    @BeforeEach
    void setUp() {
        service = new PatientResponseService(aiGateway, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Успешная генерация черновика ответа")
    void shouldGenerateDraftAnswerSuccessfully() {
        // given
        PatientQuestionRequest request = new PatientQuestionRequest(
                "Как перенести запись к терапевту?",
                "ЗАПИСЬ",
                "Иванов И.И.",
                "Поликлиника №5"
        );

        LlmResponse llmResponse = LlmResponse.of(
                "Здравствуйте, Иван Иванович! Для переноса записи обратитесь в регистратуру.",
                "gigachat",
                250
        );
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(llmResponse);

        // when
        PatientAnswerResponse response = service.generateDraftAnswer(request);

        // then
        assertThat(response.draftAnswer()).contains("Иван Иванович");
        assertThat(response.provider()).isEqualTo("gigachat");
        assertThat(response.fallbackUsed()).isFalse();
        assertThat(response.disclaimer()).contains("не является медицинской консультацией");
        verify(aiGateway, times(1)).generate(any(LlmRequest.class));
    }

    @Test
    @DisplayName("Fallback при недоступности основного провайдера")
    void shouldUseFallbackWhenProviderFails() {
        // given
        PatientQuestionRequest request = new PatientQuestionRequest(
                "Какие документы нужны?",
                "ДОКУМЕНТЫ",
                "Петрова А.С.",
                null
        );

        LlmResponse fallbackResponse = LlmResponse.fallback(
                "Сервис временно недоступен. Обратитесь в регистратуру.",
                "internal"
        );
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(fallbackResponse);

        // when
        PatientAnswerResponse response = service.generateDraftAnswer(request);

        // then
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.draftAnswer()).isNotEmpty();
    }

    @Test
    @DisplayName("Санитизация: ограничение длины ответа")
    void shouldTruncateLongAnswer() {
        // given
        PatientQuestionRequest request = new PatientQuestionRequest(
                "Вопрос", "ОБЩЕЕ", null, null
        );

        String longText = "А".repeat(1500);
        LlmResponse llmResponse = LlmResponse.of(longText, "gigachat", 100);
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(llmResponse);

        // when
        PatientAnswerResponse response = service.generateDraftAnswer(request);

        // then
        assertThat(response.draftAnswer().length()).isLessThanOrEqualTo(803); // 800 + "..."
    }

    @Test
    @DisplayName("Санитизация: удаление медицинских назначений")
    void shouldRemoveMedicalPrescriptions() {
        // given
        PatientQuestionRequest request = new PatientQuestionRequest(
                "Вопрос", "ОБЩЕЕ", null, null
        );

        String dangerousText = "Примите 500 мг парацетамола. Диагноз: ОРВИ.";
        LlmResponse llmResponse = LlmResponse.of(dangerousText, "gigachat", 100);
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(llmResponse);

        // when
        PatientAnswerResponse response = service.generateDraftAnswer(request);

        // then
        assertThat(response.draftAnswer()).doesNotContain("Примите 500 мг");
        assertThat(response.draftAnswer()).doesNotContain("Диагноз:");
    }

    @Test
    @DisplayName("Пустой ответ от LLM ? fallback-сообщение")
    void shouldHandleEmptyLlmResponse() {
        // given
        PatientQuestionRequest request = new PatientQuestionRequest(
                "Вопрос", "ОБЩЕЕ", null, null
        );

        LlmResponse emptyResponse = LlmResponse.of("", "gigachat", 50);
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(emptyResponse);

        // when
        PatientAnswerResponse response = service.generateDraftAnswer(request);

        // then
        assertThat(response.draftAnswer()).contains("не удалось сформировать ответ");
    }

    @Test
    @DisplayName("Prompt содержит системные ограничения")
    void shouldIncludeGuardrailsInPrompt() {
        // given
        PatientQuestionRequest request = new PatientQuestionRequest(
                "Как записаться?", "ЗАПИСЬ", null, null
        );

        when(aiGateway.generate(any(LlmRequest.class))).thenAnswer(invocation -> {
            LlmRequest llmRequest = invocation.getArgument(0);
            assertThat(llmRequest.systemPrompt()).contains("НИКОГДА не давай медицинских назначений");
            assertThat(llmRequest.temperature()).isEqualTo(0.3);
            return LlmResponse.of("Ответ", "gigachat", 100);
        });

        // when
        service.generateDraftAnswer(request);

        // then
        verify(aiGateway).generate(any(LlmRequest.class));
    }
}