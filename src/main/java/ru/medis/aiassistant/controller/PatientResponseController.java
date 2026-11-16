package ru.medis.aiassistant.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.medis.aiassistant.dto.PatientAnswerResponse;
import ru.medis.aiassistant.dto.PatientQuestionRequest;
import ru.medis.aiassistant.service.PatientResponseService;

/**
 * REST-контроллер для генерации черновика ответа пациенту.
 * Контроллер НЕ вызывает LLM напрямую — только через Service.
 */
@RestController
@RequestMapping("/api/v1/patient")
@CrossOrigin(origins = "http://localhost:5500")
public class PatientResponseController {

    private final PatientResponseService service;

    public PatientResponseController(PatientResponseService service) {
        this.service = service;
    }

    @PostMapping("/draft-answer")
    public ResponseEntity<PatientAnswerResponse> generateDraftAnswer(
            @Valid @RequestBody PatientQuestionRequest request) {
        PatientAnswerResponse response = service.generateDraftAnswer(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}