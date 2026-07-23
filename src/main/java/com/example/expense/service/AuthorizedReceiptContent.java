package com.example.expense.service;

import com.example.expense.dto.response.ReceiptFileResponse;

import java.io.InputStream;

public record AuthorizedReceiptContent(
        ReceiptFileResponse metadata,
        InputStream content,
        ReceiptContentDisposition disposition
) {
}
