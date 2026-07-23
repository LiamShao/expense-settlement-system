package com.example.expense.storage;

import java.io.InputStream;

public interface ReceiptStorage {

    void put(String storageKey, InputStream content, long contentLength, String contentType);

    InputStream open(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);
}
