package com.example.expense.common.enums;

public enum ExpenseStatus {
    DRAFT("下書き"),
    SUBMITTED("申請中"),
    APPROVED("承認済み"),
    RETURNED("差戻し");

    private final String displayName;

    ExpenseStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEditableByApplicant() {
        return this == DRAFT || this == RETURNED;
    }

    public boolean isReviewable() {
        return this == SUBMITTED;
    }
}
