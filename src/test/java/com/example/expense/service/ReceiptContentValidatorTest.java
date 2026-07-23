package com.example.expense.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptContentValidatorTest {

    private final ReceiptContentValidator validator = new ReceiptContentValidator();

    @Test
    void validate_正常系_PDFを検証してファイル名とSHA256を返す() {
        byte[] content = "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII);

        try (ValidatedReceiptContent validated = validator.validate(
                "../領収書.pdf",
                "application/pdf",
                new ByteArrayInputStream(content)
        )) {
            assertThat(validated.originalFileName()).isEqualTo("..領収書.pdf");
            assertThat(validated.contentType()).isEqualTo("application/pdf");
            assertThat(validated.sizeBytes()).isEqualTo(content.length);
            assertThat(validated.sha256Checksum())
                    .matches("[0-9a-f]{64}")
                    .isEqualTo("d8bbdb07ec7989be913bf5074759320c476c70086e52b80a38f7b07d416f0371");
            assertThat(validated.temporaryFile()).exists();
        }
    }

    @Test
    void validate_異常系_拡張子とContentTypeが一致しない() {
        assertThatThrownBy(() -> validator.validate(
                "receipt.png",
                "application/pdf",
                new ByteArrayInputStream("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validate_異常系_magicBytesが一致しない() {
        assertThatThrownBy(() -> validator.validate(
                "receipt.pdf",
                "application/pdf",
                new ByteArrayInputStream("<html>".getBytes(StandardCharsets.US_ASCII))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validate_異常系_未対応形式() {
        assertThatThrownBy(() -> validator.validate(
                "receipt.svg",
                "image/svg+xml",
                new ByteArrayInputStream("<svg/>".getBytes(StandardCharsets.US_ASCII))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void validate_異常系_10MiBを超える() {
        byte[] content = new byte[(int) ReceiptContentValidator.MAX_SIZE_BYTES + 1];
        content[0] = (byte) 0xff;
        content[1] = (byte) 0xd8;
        content[2] = (byte) 0xff;

        assertThatThrownBy(() -> validator.validate(
                "receipt.jpg",
                "image/jpeg",
                new ByteArrayInputStream(content)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void validate_異常系_空ファイル() {
        assertThatThrownBy(() -> validator.validate(
                "receipt.png",
                "image/png",
                new ByteArrayInputStream(new byte[0])
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
