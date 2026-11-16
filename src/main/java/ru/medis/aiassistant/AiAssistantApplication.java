package ru.medis.aiassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа сервиса генерации черновиков ответов пациентам.
 * Архитектура: Controller -> Service -> AiGateway -> LlmClient -> Provider (GigaChat / Internal).
 */
@SpringBootApplication
@ConfigurationPropertiesScan("ru.medis.aiassistant.config")
public class AiAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAssistantApplication.class, args);
    }
}
