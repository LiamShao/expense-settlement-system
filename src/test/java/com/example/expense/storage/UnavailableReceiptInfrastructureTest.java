package com.example.expense.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnavailableReceiptInfrastructureTest {

    @Test
    void storage_異常系_未設定時はfailClosedにする() {
        ReceiptStorage storage = new UnavailableReceiptStorage();

        assertThatThrownBy(() -> storage.put(
                "receipts/generated-key",
                new ByteArrayInputStream(new byte[]{1}),
                1,
                "application/pdf"
        )).isInstanceOf(ReceiptStorageException.class);
        assertThatThrownBy(() -> storage.exists("receipts/generated-key"))
                .isInstanceOf(ReceiptStorageException.class);
    }

    @Test
    void scanner_異常系_未設定時はfailClosedにする() {
        MalwareScanner scanner = new UnavailableMalwareScanner();

        assertThatThrownBy(() -> scanner.scan(new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(MalwareScannerUnavailableException.class);
    }
}
