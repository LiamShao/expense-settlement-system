package com.example.expense.dto.request;

import com.example.expense.common.enums.ExpenseStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ExpenseApplicationSearchRequest {

    private Long applicantId;
    private ExpenseStatus status;

    @Size(max = 200)
    private String keyword;

    private LocalDate expenseDateFrom;
    private LocalDate expenseDateTo;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public ExpenseStatus getStatus() {
        return status;
    }

    public void setStatus(ExpenseStatus status) {
        this.status = status;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public LocalDate getExpenseDateFrom() {
        return expenseDateFrom;
    }

    public void setExpenseDateFrom(LocalDate expenseDateFrom) {
        this.expenseDateFrom = expenseDateFrom;
    }

    public LocalDate getExpenseDateTo() {
        return expenseDateTo;
    }

    public void setExpenseDateTo(LocalDate expenseDateTo) {
        this.expenseDateTo = expenseDateTo;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getOffset() {
        return page * size;
    }
}
