package com.example.expense.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public record ValidatedReceiptContent(
        Path temporaryFile,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String sha256Checksum
) implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidatedReceiptContent.class);

    public InputStream openStream() throws IOException {
        return Files.newInputStream(temporaryFile);
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            LOGGER.warn("A temporary receipt upload file could not be deleted.");
        }
    }
}
