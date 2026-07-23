package com.example.expense.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class ReceiptStorageKeyGenerator {

    private final Clock clock;
    private final Supplier<UUID> uuidSupplier;

    public ReceiptStorageKeyGenerator() {
        this(Clock.systemUTC(), UUID::randomUUID);
    }

    ReceiptStorageKeyGenerator(Clock clock, Supplier<UUID> uuidSupplier) {
        this.clock = clock;
        this.uuidSupplier = uuidSupplier;
    }

    public String generate(Long applicationId, Long itemId) {
        LocalDate date = LocalDate.now(clock);
        return "receipts/%04d/%02d/%d/%d/%s".formatted(
                date.getYear(),
                date.getMonthValue(),
                applicationId,
                itemId,
                uuidSupplier.get()
        );
    }
}
