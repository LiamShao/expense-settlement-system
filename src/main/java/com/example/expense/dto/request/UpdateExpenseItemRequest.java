package com.example.expense.dto.request;

import jakarta.validation.constraints.Positive;

public class UpdateExpenseItemRequest extends ExpenseItemRequest {

    @Positive
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
