package com.example.expense.common;

public class ValidationErrorDetail {

    private final String field;
    private final String message;

    public ValidationErrorDetail(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }
}
