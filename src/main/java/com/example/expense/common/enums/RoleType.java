package com.example.expense.common.enums;

public enum RoleType {
    USER("一般社員"),
    APPROVER("承認者"),
    ADMIN("管理者");

    private final String displayName;

    RoleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
