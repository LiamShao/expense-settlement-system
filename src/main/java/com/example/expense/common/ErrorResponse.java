package com.example.expense.common;

import java.time.LocalDateTime;
import java.util.List;

public class ErrorResponse {

    private final boolean success = false;
    private final String code;
    private final String message;
    private final List<ValidationErrorDetail> details;
    private final String path;
    private final LocalDateTime timestamp;

    public ErrorResponse(
            String code,
            String message,
            List<ValidationErrorDetail> details,
            String path,
            LocalDateTime timestamp
    ) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.path = path;
        this.timestamp = timestamp;
    }

    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, null, path, LocalDateTime.now());
    }

    public static ErrorResponse validation(
            String message,
            List<ValidationErrorDetail> details,
            String path
    ) {
        return new ErrorResponse("VALIDATION_ERROR", message, details, path, LocalDateTime.now());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<ValidationErrorDetail> getDetails() {
        return details;
    }

    public String getPath() {
        return path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
