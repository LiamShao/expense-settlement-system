package com.example.expense;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseSettlementApplicationTests {

    @Test
    void main_正常系_アプリケーションクラスを参照できる() {
        assertThat(ExpenseSettlementApplication.class).isNotNull();
    }
}
