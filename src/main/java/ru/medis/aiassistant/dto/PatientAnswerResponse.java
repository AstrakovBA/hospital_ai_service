package ru.medis.aiassistant.dto;

public record PatientAnswerResponse(
        String draftAnswer,
        String provider,
        boolean fallbackUsed,
        long latencyMs,
        String disclaimer
) {
    public static final String MEDICAL_DISCLAIMER =
            "Данный ответ носит информационный характер и не является медицинской консультацией. " +
                    "Для получения медицинской помощи обратитесь к врачу.";
}