package com.example.expense.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ReceiptFileException extends ResponseStatusException {

    private final String code;

    private ReceiptFileException(HttpStatus status, String code, String reason, Throwable cause) {
        super(status, reason, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ReceiptFileException invalidFile(String reason) {
        return new ReceiptFileException(HttpStatus.BAD_REQUEST, "INVALID_FILE", reason, null);
    }

    public static ReceiptFileException fileTooLarge(String reason) {
        return new ReceiptFileException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", reason, null);
    }

    public static ReceiptFileException unsupportedMediaType(String reason) {
        return new ReceiptFileException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", reason, null);
    }

    public static ReceiptFileException malwareDetected(String reason) {
        return new ReceiptFileException(HttpStatus.UNPROCESSABLE_ENTITY, "MALWARE_DETECTED", reason, null);
    }

    public static ReceiptFileException serviceUnavailable(String reason, Throwable cause) {
        return new ReceiptFileException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "FILE_SERVICE_UNAVAILABLE",
                reason,
                cause
        );
    }
}
