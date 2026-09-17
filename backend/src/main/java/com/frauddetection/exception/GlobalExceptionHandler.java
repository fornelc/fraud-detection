package com.frauddetection.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Map;

// @RestControllerAdvice applies these handlers across every @RestController in the app (all
// four controllers), catching whatever exception escapes a controller method and converting
// it into one consistent JSON error shape ({error, status, timestamp}) instead of each
// controller handling its own failures differently. Spring dispatches to whichever handler
// below matches the most specific exception type — declaration order here doesn't matter.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Handles ServiceUnavailableException — defined in this package but not currently thrown
    // anywhere in the codebase. Ready for a future explicit "a dependency is down" signal,
    // distinct from the circuit-breaker's own rejection handled just below.
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.warn("Service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody(ex.getMessage(), 503));
    }

    // Catches CallNotPermittedException — thrown by resilience4j when the shared "neo4j"
    // circuit breaker is OPEN and rejects a call before even attempting to reach Neo4j. See
    // DEMO-DATA-EXPLAINED.md's circuit breaker section for the full state machine this backs.
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Map<String, Object>> handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("Circuit breaker open: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorBody("Graph database is temporarily unavailable. Circuit breaker is OPEN.", 503));
    }

    // Catches IllegalArgumentException — thrown by validation logic across the services (e.g.
    // TransactionIngestionService.validateRequest(), or an unknown accountId in
    // RiskScoringService), reusing the exception's own message as the response body.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(errorBody(ex.getMessage(), 400));
    }

    // Catches Spring's own NoResourceFoundException, fired for any request path that doesn't
    // match a real route (typo'd URL, wrong HTTP method). Returns a hint pointing at /api/
    // instead of Spring's default, less helpful 404 body.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody("No API endpoint at this path. All endpoints are under /api/", 404));
    }

    // Catch-all for anything not explicitly handled above. Logs the full exception server-side
    // (log.error, with stack trace) but returns a generic, non-leaky message to the client —
    // internal exception details never cross the API boundary.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(errorBody("An unexpected error occurred", 500));
    }

    // Builds the one consistent error shape every handler above returns.
    private Map<String, Object> errorBody(String message, int status) {
        return Map.of(
            "error", message,
            "status", status,
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
