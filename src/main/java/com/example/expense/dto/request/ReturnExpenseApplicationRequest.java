package com.example.expense.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReturnExpenseApplicationRequest {

    @NotBlank
    @Size(max = 1000)
    private String returnReason;

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }
}
