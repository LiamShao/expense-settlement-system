package com.example.expense.entity;

import com.example.expense.common.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ExpenseItem {

    private Long id;
    private Long expenseApplicationId;
    private LocalDate expenseDate;
    private ExpenseCategory category;
    private BigDecimal amount;
    private String description;
    private String receiptObjectKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExpenseApplicationId() {
        return expenseApplicationId;
    }

    public void setExpenseApplicationId(Long expenseApplicationId) {
        this.expenseApplicationId = expenseApplicationId;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
