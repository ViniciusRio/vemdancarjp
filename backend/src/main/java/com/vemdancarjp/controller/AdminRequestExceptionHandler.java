package com.vemdancarjp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {
    AdminEventController.class,
    AdminVariableVenueController.class,
    AdminOtherVenueController.class
})
class AdminRequestExceptionHandler {

    @ExceptionHandler({AdminRequestValidation.InvalidAdminRequestException.class,
        HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_ERROR"));
    }
}
