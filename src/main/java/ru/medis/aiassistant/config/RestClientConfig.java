package ru.medis.aiassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient gigachatRestClient(LlmProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getGigachat().getBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    public RestClient gigachatOAuthRestClient(LlmProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(LlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getGigachat().getTimeout().toMillis());
        factory.setReadTimeout((int) properties.getGigachat().getTimeout().toMillis());
        return factory;
    }
}