package ru.medis.aiassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientQuestionRequest(
        @NotBlank(message = "Вопрос пациента обязателен")
        @Size(max = 2000, message = "Вопрос не должен превышать 2000 символов")
        String patientQuestion,

        @NotBlank(message = "Категория вопроса обязательна")
        String category,

        @Size(max = 200)
        String patientName,

        @Size(max = 200)
        String clinicName
) {}