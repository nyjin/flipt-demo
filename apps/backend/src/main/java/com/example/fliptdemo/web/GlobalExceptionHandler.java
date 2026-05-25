package com.example.fliptdemo.web;

import com.example.fliptdemo.featureflag.FeatureDisabledException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates a disabled feature into an HTTP response. We use 404 so a disabled
 * endpoint is indistinguishable from one that does not exist (the feature is
 * "hidden"). Switch to 403/503 here if you prefer a different semantic.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleFeatureDisabled(FeatureDisabledException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Not Found",
                "flag", ex.getFlagKey());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
