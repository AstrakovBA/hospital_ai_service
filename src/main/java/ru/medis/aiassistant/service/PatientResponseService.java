package ru.medis.aiassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.medis.aiassistant.domain.QuestionCategory;
import ru.medis.aiassistant.dto.PatientAnswerResponse;
import ru.medis.aiassistant.dto.PatientQuestionRequest;
import ru.medis.aiassistant.llm.AiGateway;
import ru.medis.aiassistant.llm.LlmRequest;
import ru.medis.aiassistant.llm.LlmResponse;

import java.util.Map;

@Service
public class PatientResponseService {

    private static final Logger log = LoggerFactory.getLogger(PatientResponseService.class);
    private static final int MAX_ANSWER_LENGTH = 800;

    private final AiGateway aiGateway;

    public PatientResponseService(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    public PatientAnswerResponse generateDraftAnswer(PatientQuestionRequest request) {
        log.info("Generating draft answer | category={} | questionLength={}",
                request.category(), request.patientQuestion().length());

        // Валидация категории
        QuestionCategory category = QuestionCategory.fromString(request.category());

        // Формирование запроса к LLM
        LlmRequest llmRequest = LlmRequest.builder()
                .systemPrompt(buildSystemPrompt())
                .userPrompt(buildUserPrompt(request, category))
                .temperature(0.3) // низкая температура для предсказуемости
                .maxTokens(600)
                .metadata(Map.of(
                        "category", category.name(),
                        "requestId", java.util.UUID.randomUUID().toString()
                ))
                .build();

        // Вызов через AiGateway (бизнес-логика НЕ знает о провайдере)
        LlmResponse llmResponse = aiGateway.generate(llmRequest);

        // Постобработка: санитизация и формирование ответа
        String sanitizedAnswer = sanitize(llmResponse.text());

        return new PatientAnswerResponse(
                sanitizedAnswer,
                llmResponse.provider(),
                llmResponse.fallbackUsed(),
                llmResponse.latencyMs(),
                PatientAnswerResponse.MEDICAL_DISCLAIMER
        );
    }

    /**
     * Системный prompt: жёсткие ограничения для медицинской области.
     */
    private String buildSystemPrompt() {
        return """
                Ты — ассистент регистратуры медицинской организации.
                Твоя задача — помогать пациентам с ОРГАНИЗАЦИОННЫМИ вопросами.
                
                СТРОГИЕ ОГРАНИЧЕНИЯ:
                1. НИКОГДА не давай медицинских назначений, диагнозов, рекомендаций по лечению.
                2. НЕ интерпретируй результаты анализов.
                3. НЕ рекомендуй лекарственные препараты.
                4. Если вопрос медицинский — перенаправь к врачу.
                5. Отвечай вежливо, кратко, структурировано.
                6. Не выдумывай факты. Если не знаешь — скажи, что вопрос передан специалисту.
                7. Обращайся к пациенту по имени, если оно указано.
                """;
    }

    /**
     * Пользовательский prompt с контекстом.
     */
    private String buildUserPrompt(PatientQuestionRequest request, QuestionCategory category) {
        StringBuilder sb = new StringBuilder();
        sb.append("Категория вопроса: ").append(category.getDescription()).append("\n");

        if (request.patientName() != null && !request.patientName().isBlank()) {
            sb.append("Имя пациента: ").append(request.patientName()).append("\n");
        }
        if (request.clinicName() != null && !request.clinicName().isBlank()) {
            sb.append("Медицинская организация: ").append(request.clinicName()).append("\n");
        }

        sb.append("Вопрос пациента: ").append(request.patientQuestion()).append("\n");
        sb.append("\nСгенерируй вежливый черновик ответа для оператора.");
        return sb.toString();
    }

    /**
     * Санитизация ответа: ограничение длины, удаление опасных паттернов.
     */
    private String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "К сожалению, не удалось сформировать ответ. Пожалуйста, обратитесь в регистратуру.";
        }

        String sanitized = text.strip();

        // Ограничение длины
        if (sanitized.length() > MAX_ANSWER_LENGTH) {
            sanitized = sanitized.substring(0, MAX_ANSWER_LENGTH) + "...";
        }

        // Удаление потенциально опасных паттернов (упоминание доз, препаратов)
        sanitized = sanitized.replaceAll("(?i)(примите|выпейте|уколите)\\s+\\d+\\s*(мг|мл|таблетк)", "");
        sanitized = sanitized.replaceAll("(?i)(диагноз|у вас)\\s*:", "");

        return sanitized.strip();
    }
}