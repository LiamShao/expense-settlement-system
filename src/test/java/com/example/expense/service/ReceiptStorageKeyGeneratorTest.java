package com.example.expense.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptStorageKeyGeneratorTest {

    @Test
    void generate_正常系_Server側の年月とUUIDを含むkeyを生成する() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        ReceiptStorageKeyGenerator generator = new ReceiptStorageKeyGenerator(
                Clock.fixed(Instant.parse("2026-07-23T01:02:03Z"), ZoneOffset.UTC),
                () -> uuid
        );

        assertThat(generator.generate(10L, 20L))
                .isEqualTo("receipts/2026/07/10/20/123e4567-e89b-12d3-a456-426614174000");
    }
}
