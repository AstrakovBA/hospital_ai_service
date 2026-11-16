package ru.medis.aiassistant.dto;

import java.util.List;

/**
 * Стандартная ошибка API. Не содержит внутренних деталей и персональных данных.
 */
public record ErrorResponse(
        int status,
        String message,
        String timestamp,
        List<FieldViolation> violations
) {
    public ErrorResponse(int status, String message, String timestamp) {
        this(status, message, timestamp, List.of());
    }

    /** Детали нарушения валидации конкретного поля. */
    public record FieldViolation(String field, String message) {}
}
