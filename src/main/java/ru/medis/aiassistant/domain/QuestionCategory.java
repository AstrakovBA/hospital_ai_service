package ru.medis.aiassistant.domain;

public enum QuestionCategory {
    ЗАПИСЬ("Запись на приём, перенос, отмена"),
    ДОКУМЕНТЫ("Необходимые документы для приёма"),
    РАСПИСАНИЕ("Расписание работы врачей и кабинетов"),
    ОПЛАТА("Вопросы оплаты, ОМС, ДМС"),
    НАПРАВЛЕНИЕ("Направления к специалистам"),
    РЕЗУЛЬТАТЫ("Готовность результатов анализов (без интерпретации)"),
    ОБЩЕЕ("Общие организационные вопросы");

    private final String description;

    QuestionCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static QuestionCategory fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ОБЩЕЕ;
        }
    }
}