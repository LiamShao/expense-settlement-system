package com.example.expense.common.enums;

public enum ExpenseCategory {
    TRANSPORTATION("交通費"),
    MEAL("会議費・飲食費"),
    SUPPLIES("消耗品費"),
    ACCOMMODATION("宿泊費"),
    OTHER("その他");

    private final String displayName;

    ExpenseCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
