package com.example.expense.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class LocalReceiptStorage implements ReceiptStorage {

    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final Path root;

    public LocalReceiptStorage(Path configuredRoot) {
        if (configuredRoot == null) {
            throw new IllegalArgumentException("Receipt storage root is required.");
        }
        this.root = initializeRoot(configuredRoot);
    }

    @Override
    public void put(String storageKey, InputStream content, long contentLength, String contentType) {
        if (content == null) {
            throw new IllegalArgumentException("Receipt content is required.");
        }
        if (contentLength <= 0) {
            throw new IllegalArgumentException("Receipt content length must be positive.");
        }

        Path target = resolveStorageKey(storageKey);
        prepareParentDirectories(target.getParent());
        rejectSymbolicLink(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReceiptStorageException("Receipt object already exists.");
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), ".receipt-upload-", ".tmp");
            applyFilePermissions(temporary);
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temporary) != contentLength) {
                throw new ReceiptStorageException("Receipt content length does not match.");
            }
            moveIntoPlace(temporary, target);
            temporary = null;
        } catch (IOException exception) {
            throw new ReceiptStorageException("Failed to store receipt object.", exception);
        } finally {
            deleteTemporaryFile(temporary);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path target = resolveStorageKey(storageKey);
        rejectSymbolicLink(target);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReceiptStorageObjectNotFoundException();
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException exception) {
            throw new ReceiptStorageException("Failed to open receipt object.", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path target = resolveStorageKey(storageKey);
        rejectSymbolicLink(target);
        return Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveStorageKey(storageKey);
        rejectSymbolicLink(target);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReceiptStorageException("Receipt storage path is invalid.");
        }
        try {
            Files.delete(target);
        } catch (IOException exception) {
            throw new ReceiptStorageException("Failed to delete receipt object.", exception);
        }
    }

    Path getRoot() {
        return root;
    }

    private Path initializeRoot(Path configuredRoot) {
        Path normalizedRoot = configuredRoot.toAbsolutePath().normalize();
        try {
            boolean rootAlreadyExists = Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS);
            Files.createDirectories(normalizedRoot);
            if (Files.isSymbolicLink(normalizedRoot)
                    || !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new ReceiptStorageException("Receipt storage root is invalid.");
            }
            if (!rootAlreadyExists) {
                applyDirectoryPermissions(normalizedRoot);
            }
            return normalizedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new ReceiptStorageException("Failed to initialize receipt storage.", exception);
        }
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()
                || storageKey.indexOf('\0') >= 0
                || storageKey.contains("\\")) {
            throw new ReceiptStorageException("Receipt storage key is invalid.");
        }

        final Path relative;
        try {
            relative = Path.of(storageKey);
        } catch (RuntimeException exception) {
            throw new ReceiptStorageException("Receipt storage key is invalid.", exception);
        }

        if (relative.isAbsolute()) {
            throw new ReceiptStorageException("Receipt storage key is invalid.");
        }
        for (Path segment : relative) {
            String value = segment.toString();
            if (value.equals(".") || value.equals("..") || value.isBlank()) {
                throw new ReceiptStorageException("Receipt storage key is invalid.");
            }
        }

        Path target = root.resolve(relative).normalize();
        if (target.equals(root) || !target.startsWith(root)) {
            throw new ReceiptStorageException("Receipt storage key is invalid.");
        }
        rejectSymbolicLinkAncestors(target.getParent());
        return target;
    }

    private void prepareParentDirectories(Path parent) {
        Path current = root;
        for (Path segment : root.relativize(parent)) {
            current = current.resolve(segment);
            try {
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(current)
                            || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                        throw new ReceiptStorageException("Receipt storage path is invalid.");
                    }
                } else {
                    Files.createDirectory(current);
                    applyDirectoryPermissions(current);
                }
            } catch (IOException exception) {
                throw new ReceiptStorageException("Failed to prepare receipt storage.", exception);
            }
        }
    }

    private void rejectSymbolicLinkAncestors(Path parent) {
        if (parent == null || !parent.startsWith(root)) {
            throw new ReceiptStorageException("Receipt storage path is invalid.");
        }
        Path current = root;
        for (Path segment : root.relativize(parent)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new ReceiptStorageException("Receipt storage path is invalid.");
            }
        }
    }

    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new ReceiptStorageException("Receipt storage path is invalid.");
        }
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target);
        }
    }

    private void deleteTemporaryFile(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The failed upload remains inaccessible because temporary names are never valid storage keys.
        }
    }

    private void applyDirectoryPermissions(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, OWNER_DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on their platform ACL.
        }
    }

    private void applyFilePermissions(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, OWNER_FILE_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on their platform ACL.
        }
    }
}
