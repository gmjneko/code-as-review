package org.koaks.codereview.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "malformed request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        // Spring MVC's own exceptions (unsupported media type, method not allowed, no handler, ...)
        // carry their intended status.
        if (e instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            String detail = error.getBody().getDetail();
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(status.name(), detail != null ? detail : status.getReasonPhrase()));
        }
        log.error("Unhandled request failure", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "internal server error"));
    }
}
