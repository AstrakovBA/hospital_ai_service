package ru.medis.aiassistant.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Внутренний LLM-клиент (заглушка).
 * Используется как fallback при недоступности внешнего провайдера,
 * а также для разработки и тестирования без внешних зависимостей.
 */
@Component
public class InternalLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(InternalLlmClient.class);
    private static final String PROVIDER_NAME = "internal";

    private static final Map<String, String> TEMPLATE_ANSWERS = Map.of(
            "ЗАПИСЬ", "Здравствуйте! Для решения вопроса о записи на приём, пожалуйста, " +
                    "обратитесь в регистратуру по телефону или через портал Госуслуг. " +
                    "При себе необходимо иметь паспорт и полис ОМС.",
            "ДОКУМЕНТЫ", "Здравствуйте! Для приёма в нашей клинике вам понадобятся: " +
                    "паспорт, полис ОМС (или ДМС), СНИЛС. Если у вас есть результаты " +
                    "предыдущих обследований, рекомендуется взять их с собой.",
            "РАСПИСАНИЕ", "Здравствуйте! Актуальное расписание работы врачей и кабинетов " +
                    "доступно на сайте клиники и в регистратуре. Часы работы: Пн-Пт 8:00-20:00, " +
                    "Сб 9:00-15:00.",
            "ОПЛАТА", "Здравствуйте! Информация об оплате услуг: приём осуществляется по полису " +
                    "ОМС бесплатно. Платные услуги оплачиваются в кассе клиники или онлайн.",
            "ОБЩЕЕ", "Здравствуйте! Спасибо за ваше обращение. Ваш вопрос передан " +
                    "соответствующему специалисту. Ожидайте ответа в течение 1 рабочего дня."
    );

    @Override
    public LlmResponse generate(LlmRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Internal LLM request | category={}", request.metadata().getOrDefault("category", "ОБЩЕЕ"));

        String category = request.metadata().getOrDefault("category", "ОБЩЕЕ");
        String answer = TEMPLATE_ANSWERS.getOrDefault(category, TEMPLATE_ANSWERS.get("ОБЩЕЕ"));

        long latency = System.currentTimeMillis() - startTime;
        log.info("Internal LLM response generated | latency={}ms", latency);

        return LlmResponse.of(answer, PROVIDER_NAME, latency);
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return true; // внутренний провайдер всегда доступен
    }
}