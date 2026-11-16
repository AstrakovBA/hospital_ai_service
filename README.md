# hospital_ai_service
Медицинская информационная система с интеграцией LLM для ответов на вопросы пациентов. Учебный проект

## GigaChat

При запуске Spring Boot загружает необязательный `.env` из корня проекта. Для получения OAuth-токена задайте `GIGACHAT_CREDENTIALS` (значение Basic credentials из кабинета GigaChat) и, при необходимости, `GIGACHAT_SCOPE` и `GIGACHAT_MODEL`. Токен запрашивается у OAuth API автоматически, кешируется и обновляется перед истечением срока действия.

Также поддерживаются `Client_ID` и `CLIENT_SECRET`: если `GIGACHAT_CREDENTIALS` не задан, приложение сформирует Basic credentials из этих двух значений. `GIGACHAT_ACCESS_TOKEN` можно задать вместо OAuth-данных, чтобы использовать готовый токен. Файл `.env` не следует добавлять в Git.

## Веб-интерфейс

Запустите backend командой `mvn spring-boot:run`, а в другом терминале из корня проекта - `python -m http.server 5500 --directory frontend`. Откройте `http://localhost:5500`. Интерфейс отправляет вопросы на `http://localhost:8080/api/v1/patient/draft-answer`.
