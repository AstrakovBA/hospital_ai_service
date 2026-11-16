package ru.medis.aiassistant.llm;

/**
 * Абстракция над LLM-провайдером.
 * Бизнес-логика работает только через этот интерфейс,
 * не зная о конкретном провайдере (GigaChat, OpenAI, внутренняя модель).
 */
public interface LlmClient {

    /**
     * Генерация ответа на основе запроса.
     *
     * @param request запрос к модели (prompt, параметры)
     * @return ответ модели
     * @throws LlmUnavailableException если провайдер недоступен
     */
    LlmResponse generate(LlmRequest request);

    /**
     * Уникальное имя провайдера для маршрутизации в AiGateway.
     */
    String providerName();

    /**
     * Проверка доступности провайдера (health check).
     */
    boolean isAvailable();
}