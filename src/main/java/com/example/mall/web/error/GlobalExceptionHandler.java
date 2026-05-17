package com.example.mall.web.error;

import com.example.mall.domain.inventory.InsufficientStockException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorBody> handleConflict(ConflictException ex) {
        return body(HttpStatus.CONFLICT, "conflict", ex.getMessage(), List.of());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "not_found", ex.getMessage(), List.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorBody> handleUnauthorized(UnauthorizedException ex) {
        return body(HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .toList();
        return body(HttpStatus.BAD_REQUEST, "validation_failed", "invalid request", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleConstraint(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream().map(v -> v.getMessage()).toList();
        return body(HttpStatus.BAD_REQUEST, "validation_failed", "invalid request", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArg(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> handleIllegalState(IllegalStateException ex) {
        return body(HttpStatus.CONFLICT, "invalid_state", ex.getMessage(), List.of());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorBody> handleStock(InsufficientStockException ex) {
        return body(HttpStatus.CONFLICT, "insufficient_stock", ex.getMessage(), List.of());
    }

    private ResponseEntity<ErrorBody> body(
            HttpStatus status, String code, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(new ErrorBody(code, message, details, Instant.now()));
    }

    public record ErrorBody(String code, String message, List<String> details, Instant timestamp) {}
}
