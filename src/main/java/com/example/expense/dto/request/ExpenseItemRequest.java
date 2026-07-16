package com.example.expense.dto.request;

import com.example.expense.common.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseItemRequest {

    @NotNull
    private LocalDate expenseDate;

    @NotNull
    private ExpenseCategory category;

    @NotNull
    @DecimalMin(value = "1")
    @DecimalMax(value = "999999999999")
    @Digits(integer = 12, fraction = 0)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 500)
    private String description;

    @Size(max = 500)
    private String receiptObjectKey;

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReceiptObjectKey() {
        return receiptObjectKey;
    }

    public void setReceiptObjectKey(String receiptObjectKey) {
        this.receiptObjectKey = receiptObjectKey;
    }
}
