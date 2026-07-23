package com.example.expense.service;

import com.example.expense.common.enums.ReceiptFileState;
import com.example.expense.entity.ReceiptFile;
import com.example.expense.repository.ReceiptFileMapper;
import com.example.expense.storage.ReceiptStorage;
import com.example.expense.storage.ReceiptStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ReceiptFileCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptFileCleanupService.class);
    private static final List<ReceiptFileState> CLEANUP_STATES = List.of(
            ReceiptFileState.UPLOADING,
            ReceiptFileState.PENDING_SCAN,
            ReceiptFileState.REJECTED,
            ReceiptFileState.PENDING_DELETE
    );

    private final ReceiptFileMapper receiptFileMapper;
    private final ReceiptStorage receiptStorage;
    private final TransactionTemplate transactionTemplate;

    public ReceiptFileCleanupService(
            ReceiptFileMapper receiptFileMapper,
            ReceiptStorage receiptStorage,
            TransactionTemplate transactionTemplate
    ) {
        this.receiptFileMapper = receiptFileMapper;
        this.receiptStorage = receiptStorage;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean cleanupNonActive(Long receiptFileId, String storageKey) {
        ReceiptFile claimed = transactionTemplate.execute(status -> claimForCleanup(receiptFileId));
        if (claimed == null) {
            return false;
        }
        if (!Objects.equals(claimed.getStorageKey(), storageKey)) {
            throw new IllegalStateException("Receipt cleanup storage key does not match metadata.");
        }

        receiptStorage.delete(claimed.getStorageKey());
        Boolean deleted = transactionTemplate.execute(status ->
                receiptFileMapper.deleteByIdAndState(receiptFileId, ReceiptFileState.PENDING_DELETE) == 1
        );
        return Boolean.TRUE.equals(deleted);
    }

    public void cleanupBestEffort(Long receiptFileId, String storageKey) {
        try {
            cleanupNonActive(receiptFileId, storageKey);
        } catch (RuntimeException exception) {
            LOGGER.warn("Receipt cleanup is deferred for metadata id {}.", receiptFileId);
        }
    }

    public int cleanupStale(Duration minimumAge, int limit) {
        if (minimumAge == null || minimumAge.isNegative() || minimumAge.isZero()) {
            throw new IllegalArgumentException("Cleanup minimum age must be positive.");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Cleanup limit must be between 1 and 1000.");
        }

        List<ReceiptFile> candidates = receiptFileMapper.findStaleByStates(
                CLEANUP_STATES,
                LocalDateTime.now().minus(minimumAge),
                limit
        );
        int cleaned = 0;
        for (ReceiptFile candidate : candidates) {
            try {
                if (cleanupNonActive(candidate.getId(), candidate.getStorageKey())) {
                    cleaned++;
                }
            } catch (ReceiptStorageException exception) {
                LOGGER.warn("Receipt cleanup retry failed for metadata id {}.", candidate.getId());
            }
        }
        return cleaned;
    }

    private ReceiptFile claimForCleanup(Long receiptFileId) {
        ReceiptFile current = receiptFileMapper.findByIdForUpdate(receiptFileId);
        if (current == null || current.getState() == ReceiptFileState.ACTIVE) {
            return null;
        }
        if (current.getState() != ReceiptFileState.PENDING_DELETE) {
            int updated = receiptFileMapper.transitionState(
                    current.getId(),
                    current.getState(),
                    ReceiptFileState.PENDING_DELETE,
                    null
            );
            if (updated != 1) {
                return null;
            }
            current.setState(ReceiptFileState.PENDING_DELETE);
        }
        return current;
    }
}
