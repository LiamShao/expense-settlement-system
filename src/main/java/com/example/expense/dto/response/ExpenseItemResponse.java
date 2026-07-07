package com.example.expense.dto.response;

import com.example.expense.common.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseItemResponse {

    private Long id;
    private LocalDate expenseDate;
    private ExpenseCategory category;
    private String categoryName;
    private BigDecimal amount;
    private String description;
    private String receiptObjectKey;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
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
