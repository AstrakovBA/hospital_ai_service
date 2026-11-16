package ru.medis.aiassistant.llm;

/**
 * ”ниверсальный ответ от LLM.
 */
public record LlmResponse(
        String text,
        String provider,
        boolean fallbackUsed,
        long latencyMs
) {
    public static LlmResponse fallback(String message, String provider) {
        return new LlmResponse(message, provider, true, 0);
    }

    public static LlmResponse of(String text, String provider, long latencyMs) {
        return new LlmResponse(text, provider, false, latencyMs);
    }
}