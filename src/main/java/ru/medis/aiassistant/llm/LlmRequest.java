package ru.medis.aiassistant.llm;

import java.util.Map;

/**
 * Универсальный запрос к LLM (не привязан к конкретному провайдеру).
 */
public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        double temperature,
        int maxTokens,
        Map<String, String> metadata
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String systemPrompt = "";
        private String userPrompt = "";
        private double temperature = 0.3;
        private int maxTokens = 512;
        private Map<String, String> metadata = Map.of();

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = Math.max(0.0, Math.min(1.0, temperature));
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = Math.max(1, Math.min(4096, maxTokens));
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(systemPrompt, userPrompt, temperature, maxTokens, metadata);
        }
    }
}