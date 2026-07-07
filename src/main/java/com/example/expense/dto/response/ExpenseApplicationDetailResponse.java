package com.example.expense.dto.response;

import com.example.expense.common.enums.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ExpenseApplicationDetailResponse {

    private Long id;
    private UserResponse applicant;
    private String title;
    private ExpenseStatus status;
    private String statusName;
    private BigDecimal totalAmount;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private UserResponse approver;
    private LocalDateTime returnedAt;
    private String returnReason;
    private List<ExpenseItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserResponse getApplicant() {
        return applicant;
    }

    public void setApplicant(UserResponse applicant) {
        this.applicant = applicant;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ExpenseStatus getStatus() {
        return status;
    }

    public void setStatus(ExpenseStatus status) {
        this.status = status;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public UserResponse getApprover() {
        return approver;
    }

    public void setApprover(UserResponse approver) {
        this.approver = approver;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public List<ExpenseItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ExpenseItemResponse> items) {
        this.items = items;
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
