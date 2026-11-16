package ru.medis.aiassistant.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.medis.aiassistant.llm.AiGateway;
import ru.medis.aiassistant.llm.LlmResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Интеграционные тесты")
class PatientResponseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiGateway aiGateway;

    @Test
    @DisplayName("POST /api/v1/patient/draft-answer — успешный ответ")
    void shouldReturnDraftAnswer() throws Exception {
        when(aiGateway.generate(any()))
                .thenReturn(LlmResponse.of("Здравствуйте! Для записи...", "gigachat", 200));

        String json = """
                {
                  "patientQuestion": "Как записаться к врачу?",
                  "category": "ЗАПИСЬ",
                  "patientName": "Иванов И.И.",
                  "clinicName": "Поликлиника №5"
                }
                """;

        mockMvc.perform(post("/api/v1/patient/draft-answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftAnswer").isNotEmpty())
                .andExpect(jsonPath("$.provider").value("gigachat"))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/patient/draft-answer — валидация пустого вопроса")
    void shouldRejectEmptyQuestion() throws Exception {
        String json = """
                {
                  "patientQuestion": "",
                  "category": "ЗАПИСЬ"
                }
                """;

        mockMvc.perform(post("/api/v1/patient/draft-answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}