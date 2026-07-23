package com.example.expense.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ReceiptContentValidator {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf"
    );
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    public ValidatedReceiptContent validate(
            String originalFileName,
            String declaredContentType,
            InputStream content
    ) {
        String sanitizedFileName = sanitizeFileName(originalFileName);
        String contentType = validateDeclaredType(sanitizedFileName, declaredContentType);
        if (content == null) {
            throw invalidFile("ファイルを選択してください。");
        }

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("receipt-validation-", ".tmp");
            applyOwnerOnlyPermissions(temporaryFile);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyAndDigest(content, temporaryFile, digest);
            validateSignature(temporaryFile, contentType);
            return new ValidatedReceiptContent(
                    temporaryFile,
                    sanitizedFileName,
                    contentType,
                    size,
                    HexFormat.of().formatHex(digest.digest())
            );
        } catch (ReceiptFileException exception) {
            deleteTemporaryFile(temporaryFile);
            throw exception;
        } catch (IOException exception) {
            deleteTemporaryFile(temporaryFile);
            throw ReceiptFileException.serviceUnavailable("領収書ファイルを一時処理できません。", exception);
        } catch (NoSuchAlgorithmException exception) {
            deleteTemporaryFile(temporaryFile);
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    String sanitizeFileName(String originalFileName) {
        if (originalFileName == null) {
            throw invalidFile("ファイル名が必要です。");
        }
        String normalized = Normalizer.normalize(originalFileName, Normalizer.Form.NFKC);
        StringBuilder sanitized = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(codePoint -> codePoint != '/' && codePoint != '\\' && !Character.isISOControl(codePoint))
                .forEach(sanitized::appendCodePoint);
        String result = sanitized.toString().trim();
        if (result.isBlank()) {
            throw invalidFile("ファイル名が必要です。");
        }
        if (result.length() > 255) {
            throw invalidFile("ファイル名は255文字以内にしてください。");
        }
        return result;
    }

    private String validateDeclaredType(String fileName, String declaredContentType) {
        if (declaredContentType == null) {
            throw unsupportedMediaType();
        }
        String normalizedType = declaredContentType.trim().toLowerCase(Locale.ROOT);
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == fileName.length() - 1) {
            throw invalidFile("対応する拡張子が必要です。");
        }
        String extension = fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        String expectedType = CONTENT_TYPES_BY_EXTENSION.get(extension);
        if (expectedType == null || !expectedType.equals(normalizedType)) {
            if (!CONTENT_TYPES_BY_EXTENSION.containsValue(normalizedType)) {
                throw unsupportedMediaType();
            }
            throw invalidFile("ファイルの拡張子とContent-Typeが一致しません。");
        }
        return normalizedType;
    }

    private long copyAndDigest(InputStream content, Path temporaryFile, MessageDigest digest) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(temporaryFile)) {
            int read;
            while ((read = content.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SIZE_BYTES) {
                    throw ReceiptFileException.fileTooLarge("領収書ファイルは10 MiB以下にしてください。");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw invalidFile("空のファイルは登録できません。");
        }
        return total;
    }

    private void validateSignature(Path temporaryFile, String contentType) throws IOException {
        byte[] prefix = new byte[8];
        int length;
        try (InputStream input = Files.newInputStream(temporaryFile)) {
            length = input.read(prefix);
        }
        boolean valid = switch (contentType) {
            case "image/jpeg" -> startsWith(prefix, length, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "image/png" -> startsWith(
                    prefix,
                    length,
                    new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
            );
            case "application/pdf" -> startsWith(prefix, length, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d});
            default -> false;
        };
        if (!valid) {
            throw invalidFile("ファイル形式を確認できません。");
        }
    }

    private boolean startsWith(byte[] actual, int actualLength, byte[] expected) {
        if (actualLength < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (actual[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private ReceiptFileException invalidFile(String message) {
        return ReceiptFileException.invalidFile(message);
    }

    private ReceiptFileException unsupportedMediaType() {
        return ReceiptFileException.unsupportedMediaType("JPEG、PNG、PDFのみ登録できます。");
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // The validation failed and this path is never exposed as a receipt object.
        }
    }

    private void applyOwnerOnlyPermissions(Path temporaryFile) throws IOException {
        try {
            Files.setPosixFilePermissions(temporaryFile, OWNER_ONLY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on their platform ACL.
        }
    }
}
