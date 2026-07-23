package com.example.expense.storage;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.InputStream;
import java.util.Set;

public class S3ReceiptStorage implements ReceiptStorage {

    private static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );

    private final S3Client s3Client;
    private final String bucket;

    public S3ReceiptStorage(S3Client s3Client, String bucket) {
        if (s3Client == null) {
            throw new IllegalArgumentException("S3 client is required.");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Receipt S3 bucket is required.");
        }
        this.s3Client = s3Client;
        this.bucket = bucket.trim();
    }

    @Override
    public void put(String storageKey, InputStream content, long contentLength, String contentType) {
        String key = validateStorageKey(storageKey);
        if (content == null) {
            throw new IllegalArgumentException("Receipt content is required.");
        }
        if (contentLength <= 0 || contentLength > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Receipt content length is invalid.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Receipt content type is invalid.");
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .ifNoneMatch("*")
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
        } catch (SdkException exception) {
            throw storageFailure("store", exception);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(validateStorageKey(storageKey))
                .build();
        try {
            return s3Client.getObject(request);
        } catch (S3Exception exception) {
            if (isNotFound(exception)) {
                throw new ReceiptStorageObjectNotFoundException();
            }
            throw storageFailure("open", exception);
        } catch (SdkException exception) {
            throw storageFailure("open", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(validateStorageKey(storageKey))
                .build();
        try {
            s3Client.headObject(request);
            return true;
        } catch (S3Exception exception) {
            if (isNotFound(exception)) {
                return false;
            }
            throw storageFailure("inspect", exception);
        } catch (SdkException exception) {
            throw storageFailure("inspect", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(validateStorageKey(storageKey))
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            throw storageFailure("delete", exception);
        }
    }

    private String validateStorageKey(String storageKey) {
        if (storageKey == null
                || storageKey.isBlank()
                || !storageKey.startsWith("receipts/")
                || storageKey.startsWith("/")
                || storageKey.endsWith("/")
                || storageKey.indexOf('\0') >= 0
                || storageKey.contains("\\")) {
            throw new ReceiptStorageException("Receipt storage key is invalid.");
        }
        for (String segment : storageKey.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new ReceiptStorageException("Receipt storage key is invalid.");
            }
        }
        return storageKey;
    }

    private boolean isNotFound(S3Exception exception) {
        return exception.statusCode() == 404;
    }

    private ReceiptStorageException storageFailure(String operation, SdkException cause) {
        return new ReceiptStorageException("Failed to %s receipt object.".formatted(operation), cause);
    }
}
