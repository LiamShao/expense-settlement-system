package com.example.expense.storage;

import java.io.InputStream;

public class UnavailableReceiptStorage implements ReceiptStorage {

    private ReceiptStorageException unavailable() {
        return new ReceiptStorageException("Receipt storage is not configured.");
    }

    @Override
    public void put(String storageKey, InputStream content, long contentLength, String contentType) {
        throw unavailable();
    }

    @Override
    public InputStream open(String storageKey) {
        throw unavailable();
    }

    @Override
    public boolean exists(String storageKey) {
        throw unavailable();
    }

    @Override
    public void delete(String storageKey) {
        throw unavailable();
    }
}
