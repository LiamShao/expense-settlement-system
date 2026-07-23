package com.example.expense.storage;

public class ReceiptStorageObjectNotFoundException extends ReceiptStorageException {

    public ReceiptStorageObjectNotFoundException() {
        super("Receipt object was not found.");
    }
}
