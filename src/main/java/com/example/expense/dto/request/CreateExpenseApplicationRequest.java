package com.example.expense.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateExpenseApplicationRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @Valid
    @NotEmpty
    private List<ExpenseItemRequest> items;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ExpenseItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ExpenseItemRequest> items) {
        this.items = items;
    }
}
