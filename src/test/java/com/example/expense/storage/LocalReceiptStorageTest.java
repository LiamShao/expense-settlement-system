package com.example.expense.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalReceiptStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void putOpenDelete_正常系_privateRoot内でstreamを保存する() throws IOException {
        LocalReceiptStorage storage = new LocalReceiptStorage(temporaryDirectory.resolve("receipts"));
        String key = "receipts/2026/07/10/100/receipt-id";
        byte[] content = "receipt-content".getBytes(StandardCharsets.UTF_8);

        storage.put(key, new ByteArrayInputStream(content), content.length, "application/pdf");

        assertThat(storage.exists(key)).isTrue();
        try (InputStream stored = storage.open(key)) {
            assertThat(stored.readAllBytes()).isEqualTo(content);
        }

        storage.delete(key);
        storage.delete(key);

        assertThat(storage.exists(key)).isFalse();
        assertThatThrownBy(() -> storage.open(key))
                .isInstanceOf(ReceiptStorageObjectNotFoundException.class);
    }

    @Test
    void put_異常系_root外pathとabsolutePathを拒否する() {
        LocalReceiptStorage storage = new LocalReceiptStorage(temporaryDirectory.resolve("receipts"));

        assertThatThrownBy(() -> storage.put(
                "../outside",
                new ByteArrayInputStream(new byte[]{1}),
                1,
                "application/pdf"
        )).isInstanceOf(ReceiptStorageException.class);
        assertThatThrownBy(() -> storage.put(
                temporaryDirectory.resolve("absolute").toString(),
                new ByteArrayInputStream(new byte[]{1}),
                1,
                "application/pdf"
        )).isInstanceOf(ReceiptStorageException.class);
        assertThatThrownBy(() -> storage.put(
                "receipts\\windows-path",
                new ByteArrayInputStream(new byte[]{1}),
                1,
                "application/pdf"
        )).isInstanceOf(ReceiptStorageException.class);
    }

    @Test
    void put_異常系_existingObjectを上書きしない() throws IOException {
        LocalReceiptStorage storage = new LocalReceiptStorage(temporaryDirectory.resolve("receipts"));
        String key = "receipts/2026/07/existing";
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        storage.put(key, new ByteArrayInputStream(original), original.length, "application/pdf");

        assertThatThrownBy(() -> storage.put(
                key,
                new ByteArrayInputStream("replacement".getBytes(StandardCharsets.UTF_8)),
                11,
                "application/pdf"
        )).isInstanceOf(ReceiptStorageException.class);

        try (InputStream stored = storage.open(key)) {
            assertThat(stored.readAllBytes()).isEqualTo(original);
        }
    }

    @Test
    void put_異常系_stream失敗時にtemporaryFileを残さない() throws IOException {
        LocalReceiptStorage storage = new LocalReceiptStorage(temporaryDirectory.resolve("receipts"));
        String key = "receipts/2026/07/failure";
        InputStream failingStream = new InputStream() {
            private int count;

            @Override
            public int read() throws IOException {
                if (count++ < 3) {
                    return 'a';
                }
                throw new IOException("simulated");
            }
        };

        assertThatThrownBy(() -> storage.put(key, failingStream, 10, "application/pdf"))
                .isInstanceOf(ReceiptStorageException.class);
        assertThat(storage.exists(key)).isFalse();
        try (var paths = Files.walk(storage.getRoot())) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void put_異常系_contentLength不一致ならobjectを公開しない() throws IOException {
        LocalReceiptStorage storage = new LocalReceiptStorage(temporaryDirectory.resolve("receipts"));
        String key = "receipts/2026/07/length-mismatch";

        assertThatThrownBy(() -> storage.put(
                key,
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                4,
                "application/pdf"
        )).isInstanceOf(ReceiptStorageException.class);

        assertThat(storage.exists(key)).isFalse();
        try (var paths = Files.walk(storage.getRoot())) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void open_異常系_symbolicLinkを辿らない() throws IOException {
        LocalReceiptStorage storage = new LocalReceiptStorage(temporaryDirectory.resolve("receipts"));
        Path outside = temporaryDirectory.resolve("outside.txt");
        Files.writeString(outside, "secret");
        Path link = storage.getRoot().resolve("linked");

        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "Symbolic links are not supported in this environment.");
        }

        assertThatThrownBy(() -> storage.open("linked"))
                .isInstanceOf(ReceiptStorageException.class);
    }
}
