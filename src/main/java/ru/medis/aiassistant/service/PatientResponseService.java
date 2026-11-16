package ru.medis.aiassistant.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.medis.aiassistant.domain.QuestionCategory;
import ru.medis.aiassistant.dto.PatientAnswerResponse;
import ru.medis.aiassistant.dto.PatientQuestionRequest;
import ru.medis.aiassistant.llm.AiGateway;
import ru.medis.aiassistant.llm.LlmRequest;
import ru.medis.aiassistant.llm.LlmResponse;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class PatientResponseService {

    private static final Logger log = LoggerFactory.getLogger(PatientResponseService.class);
    private static final int MAX_ANSWER_LENGTH = 800;

    /**
     * Опасные медицинские паттерны, которые не должны попадать в черновик ответа:
     * диагнозы, назначения и дозировки.
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // "диагноз: ...", "у вас: ..." — формулировки диагноза
            Pattern.compile("(?i)\\b(предварительный\\s+)?(диагноз|у\\s+вас|вам\\s+поставили)\\s*:?\\s*[^.;\\n]*[.;]?"),
            // "назначения:", "лечение:", "рекомендовано:" — блоки назначений
            Pattern.compile("(?i)\\b(назначени(е|я)|лечени(е|ем)|схема\\s+приёма|рекомендован(о|ы))\\s*:?\\s*[^.;\\n]*[.;]?"),
            // Прямые указания принять препарат с дозой: "примите 500 мг амоксиклава"
            Pattern.compile("(?i)\\b(принимать|примите|выпейте|уколите|капайте)\\b[^.;\\n]*"),
            // Дозировки: "по 250 мг 3 раза в день", "10 мл", "2 таблетки"
            Pattern.compile("(?i)\\b(по\\s+)?\\d+([.,]\\d+)?\\s*(мг|мл|мкг|г|таблетк|капл|укол|раз(?:а)?\\s+в\\s+(?:день|неделю))\\b[^.;\\n]*"),
            // Названия препаратов с формой выпуска (часто встречаются в галлюцинациях модели)
            Pattern.compile("(?i)\\b(таблетк|капсул|инъекц|раствор|сироп|маз)[а-яё]*\\s*(\\#\\s*\\d+)?")
    );

    private final AiGateway aiGateway;
    private final MeterRegistry meterRegistry;
    private final Timer draftTimer;
    private final Counter sanitizedCounter;

    public PatientResponseService(AiGateway aiGateway, MeterRegistry meterRegistry) {
        this.aiGateway = aiGateway;
        this.meterRegistry = meterRegistry;
        this.draftTimer = Timer.builder("draft.answer.duration")
                .description("Длительность генерации черновика ответа")
                .register(meterRegistry);
        this.sanitizedCounter = Counter.builder("draft.answer.sanitized")
                .description("Сколько раз санитизация изменила ответ модели")
                .register(meterRegistry);
    }

    public PatientAnswerResponse generateDraftAnswer(PatientQuestionRequest request) {
        // В логи попадает только категория и длина вопроса — без текста и ПД пациента
        log.info("Generating draft answer | category={} | questionLength={}",
                request.category(), request.patientQuestion().length());
        long startNanos = System.nanoTime();

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
        String rawAnswer = llmResponse.text();
        String sanitizedAnswer = sanitize(rawAnswer);
        if (!sanitizedAnswer.equals(rawAnswer == null ? "" : rawAnswer.strip())) {
            sanitizedCounter.increment();
            log.warn("Dangerous medical patterns removed from model answer | requestId={} | provider={}",
                    llmRequest.metadata().get("requestId"), llmResponse.provider());
        }

        meterRegistry.counter("draft.answer.total",
                "provider", llmResponse.provider(),
                "fallback", String.valueOf(llmResponse.fallbackUsed())).increment();
        draftTimer.record(java.time.Duration.ofNanos(System.nanoTime() - startNanos));

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
     * Санитизация ответа: ограничение длины, удаление опасных медицинских паттернов
     * (назначения, дозировки, формулировки диагнозов).
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

        // Удаление потенциально опасных паттернов (дозы, назначения, диагнозы)
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }

        // Схлопывание артефактов после удаления фрагментов
        sanitized = sanitized.replaceAll("[ \\t]{2,}", " ")
                .replaceAll("(?m)^\\s*$\\n", "")
                .strip();

        if (sanitized.isEmpty()) {
            return "К сожалению, сгенерированный ответ содержал недопустимые медицинские формулировки "
                    + "и был заблокирован. Пожалуйста, уточните вопрос в регистратуре.";
        }

        return sanitized;
    }
}