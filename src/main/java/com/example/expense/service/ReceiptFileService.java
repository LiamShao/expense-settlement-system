package com.example.expense.service;

import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.common.enums.ReceiptFileState;
import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.response.ReceiptFileResponse;
import com.example.expense.entity.ExpenseApplication;
import com.example.expense.entity.ExpenseItem;
import com.example.expense.entity.ReceiptFile;
import com.example.expense.repository.ExpenseApplicationMapper;
import com.example.expense.repository.ExpenseItemMapper;
import com.example.expense.repository.ReceiptFileMapper;
import com.example.expense.security.SecurityUser;
import com.example.expense.storage.MalwareScanResult;
import com.example.expense.storage.MalwareScanner;
import com.example.expense.storage.MalwareScannerUnavailableException;
import com.example.expense.storage.ReceiptStorage;
import com.example.expense.storage.ReceiptStorageException;
import com.example.expense.storage.ReceiptStorageObjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ReceiptFileService {

    private final ExpenseApplicationMapper expenseApplicationMapper;
    private final ExpenseItemMapper expenseItemMapper;
    private final ReceiptFileMapper receiptFileMapper;
    private final ReceiptStorage receiptStorage;
    private final MalwareScanner malwareScanner;
    private final ReceiptContentValidator contentValidator;
    private final ReceiptStorageKeyGenerator storageKeyGenerator;
    private final ReceiptFileCleanupService cleanupService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public ReceiptFileService(
            ExpenseApplicationMapper expenseApplicationMapper,
            ExpenseItemMapper expenseItemMapper,
            ReceiptFileMapper receiptFileMapper,
            ReceiptStorage receiptStorage,
            MalwareScanner malwareScanner,
            ReceiptContentValidator contentValidator,
            ReceiptStorageKeyGenerator storageKeyGenerator,
            ReceiptFileCleanupService cleanupService,
            AuditLogService auditLogService,
            TransactionTemplate transactionTemplate
    ) {
        this.expenseApplicationMapper = expenseApplicationMapper;
        this.expenseItemMapper = expenseItemMapper;
        this.receiptFileMapper = receiptFileMapper;
        this.receiptStorage = receiptStorage;
        this.malwareScanner = malwareScanner;
        this.contentValidator = contentValidator;
        this.storageKeyGenerator = storageKeyGenerator;
        this.cleanupService = cleanupService;
        this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate;
    }

    public ReceiptFileResponse uploadOrReplace(
            Long applicationId,
            Long itemId,
            String originalFileName,
            String declaredContentType,
            InputStream content,
            SecurityUser securityUser
    ) {
        transactionTemplate.executeWithoutResult(status ->
                loadWriteContext(applicationId, itemId, securityUser)
        );

        try (ValidatedReceiptContent validated =
                     contentValidator.validate(originalFileName, declaredContentType, content)) {
            UploadContext uploadContext = transactionTemplate.execute(status ->
                    createUploadingMetadata(applicationId, itemId, validated, securityUser)
            );
            if (uploadContext == null) {
                throw conflict();
            }

            storeValidatedContent(uploadContext.receiptFile(), validated);
            markPendingScan(uploadContext.receiptFile(), validated);
            scan(uploadContext.receiptFile(), validated);

            ActivationResult activation;
            try {
                activation = transactionTemplate.execute(status ->
                        activate(
                                applicationId,
                                itemId,
                                uploadContext,
                                validated,
                                securityUser
                        )
                );
            } catch (RuntimeException exception) {
                cleanupService.cleanupBestEffort(
                        uploadContext.receiptFile().getId(),
                        uploadContext.receiptFile().getStorageKey()
                );
                throw exception;
            }
            if (activation == null) {
                cleanupService.cleanupBestEffort(
                        uploadContext.receiptFile().getId(),
                        uploadContext.receiptFile().getStorageKey()
                );
                throw conflict();
            }
            if (activation.replacedReceipt() != null) {
                cleanupService.cleanupBestEffort(
                        activation.replacedReceipt().getId(),
                        activation.replacedReceipt().getStorageKey()
                );
            }
            return activation.response();
        }
    }

    public ReceiptFileResponse getMetadata(
            Long applicationId,
            Long itemId,
            SecurityUser securityUser
    ) {
        return transactionTemplate.execute(status ->
                toResponse(loadReadableActiveReceipt(applicationId, itemId, securityUser))
        );
    }

    public AuthorizedReceiptContent openContent(
            Long applicationId,
            Long itemId,
            ReceiptContentDisposition disposition,
            SecurityUser securityUser
    ) {
        if (disposition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "表示方法を指定してください。");
        }
        ReceiptFile receiptFile = transactionTemplate.execute(status ->
                loadReadableActiveReceipt(applicationId, itemId, securityUser)
        );
        if (receiptFile == null) {
            throw notFound("領収書が見つかりません。");
        }

        InputStream stream;
        try {
            stream = receiptStorage.open(receiptFile.getStorageKey());
        } catch (ReceiptStorageObjectNotFoundException exception) {
            throw notFound("領収書ファイルが見つかりません。");
        } catch (ReceiptStorageException exception) {
            throw fileServiceUnavailable(exception);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> auditLogService.record(
                    securityUser,
                    disposition == ReceiptContentDisposition.INLINE
                            ? AuditLogService.ACTION_RECEIPT_PREVIEW
                            : AuditLogService.ACTION_RECEIPT_DOWNLOAD,
                    AuditLogService.TARGET_RECEIPT_FILE,
                    receiptFile.getId(),
                    auditDetail(applicationId, itemId, receiptFile)
            ));
        } catch (RuntimeException exception) {
            closeQuietly(stream);
            throw exception;
        }
        return new AuthorizedReceiptContent(toResponse(receiptFile), stream, disposition);
    }

    public void delete(
            Long applicationId,
            Long itemId,
            SecurityUser securityUser
    ) {
        ReceiptFile receiptFile = transactionTemplate.execute(status -> {
            loadWriteContext(applicationId, itemId, securityUser);
            ReceiptFile active = receiptFileMapper.findActiveByExpenseItemIdForUpdate(itemId);
            if (active == null) {
                throw notFound("領収書が見つかりません。");
            }
            if (receiptFileMapper.transitionState(
                    active.getId(),
                    ReceiptFileState.ACTIVE,
                    ReceiptFileState.PENDING_DELETE,
                    null
            ) != 1) {
                throw conflict();
            }
            active.setState(ReceiptFileState.PENDING_DELETE);
            auditLogService.record(
                    securityUser,
                    AuditLogService.ACTION_RECEIPT_DELETE,
                    AuditLogService.TARGET_RECEIPT_FILE,
                    active.getId(),
                    auditDetail(applicationId, itemId, active)
            );
            return active;
        });
        if (receiptFile != null) {
            cleanupService.cleanupBestEffort(receiptFile.getId(), receiptFile.getStorageKey());
        }
    }

    private UploadContext createUploadingMetadata(
            Long applicationId,
            Long itemId,
            ValidatedReceiptContent validated,
            SecurityUser securityUser
    ) {
        loadWriteContext(applicationId, itemId, securityUser);
        ReceiptFile active = receiptFileMapper.findActiveByExpenseItemIdForUpdate(itemId);

        ReceiptFile receiptFile = new ReceiptFile();
        receiptFile.setExpenseItemId(itemId);
        receiptFile.setStorageKey(storageKeyGenerator.generate(applicationId, itemId));
        receiptFile.setOriginalFileName(validated.originalFileName());
        receiptFile.setState(ReceiptFileState.UPLOADING);
        receiptFile.setUploadedBy(securityUser.getId());
        if (receiptFileMapper.insert(receiptFile) != 1) {
            throw conflict();
        }
        return new UploadContext(receiptFile, active == null ? null : active.getId());
    }

    private void storeValidatedContent(ReceiptFile receiptFile, ValidatedReceiptContent validated) {
        try (InputStream input = validated.openStream()) {
            receiptStorage.put(
                    receiptFile.getStorageKey(),
                    input,
                    validated.sizeBytes(),
                    validated.contentType()
            );
        } catch (IOException | ReceiptStorageException exception) {
            cleanupService.cleanupBestEffort(receiptFile.getId(), receiptFile.getStorageKey());
            throw fileServiceUnavailable(exception);
        }
    }

    private void markPendingScan(ReceiptFile receiptFile, ValidatedReceiptContent validated) {
        try {
            Integer updated = transactionTemplate.execute(status -> receiptFileMapper.markPendingScan(
                    receiptFile.getId(),
                    validated.contentType(),
                    validated.sizeBytes(),
                    validated.sha256Checksum()
            ));
            if (!Objects.equals(updated, 1)) {
                throw conflict();
            }
            receiptFile.setContentType(validated.contentType());
            receiptFile.setSizeBytes(validated.sizeBytes());
            receiptFile.setSha256Checksum(validated.sha256Checksum());
            receiptFile.setState(ReceiptFileState.PENDING_SCAN);
        } catch (RuntimeException exception) {
            cleanupService.cleanupBestEffort(receiptFile.getId(), receiptFile.getStorageKey());
            throw exception;
        }
    }

    private void scan(ReceiptFile receiptFile, ValidatedReceiptContent validated) {
        final MalwareScanResult result;
        try (InputStream input = validated.openStream()) {
            result = malwareScanner.scan(input);
        } catch (MalwareScannerUnavailableException exception) {
            throw fileServiceUnavailable(exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw fileServiceUnavailable(exception);
        }
        if (result == null || result.verdict() == null) {
            throw fileServiceUnavailable(null);
        }
        if (result.verdict() == MalwareScanResult.Verdict.INFECTED) {
            transactionTemplate.executeWithoutResult(status -> {
                if (receiptFileMapper.transitionState(
                        receiptFile.getId(),
                        ReceiptFileState.PENDING_SCAN,
                        ReceiptFileState.REJECTED,
                        null
                ) != 1) {
                    throw conflict();
                }
            });
            receiptFile.setState(ReceiptFileState.REJECTED);
            cleanupService.cleanupBestEffort(receiptFile.getId(), receiptFile.getStorageKey());
            throw ReceiptFileException.malwareDetected("危険なファイルを検出しました。");
        }
    }

    private ActivationResult activate(
            Long applicationId,
            Long itemId,
            UploadContext uploadContext,
            ValidatedReceiptContent validated,
            SecurityUser securityUser
    ) {
        loadWriteContext(applicationId, itemId, securityUser);
        ReceiptFile currentActive = receiptFileMapper.findActiveByExpenseItemIdForUpdate(itemId);
        Long currentActiveId = currentActive == null ? null : currentActive.getId();
        if (!Objects.equals(currentActiveId, uploadContext.originalActiveReceiptId())) {
            throw conflict();
        }

        if (currentActive != null && receiptFileMapper.transitionState(
                currentActive.getId(),
                ReceiptFileState.ACTIVE,
                ReceiptFileState.PENDING_DELETE,
                null
        ) != 1) {
            throw conflict();
        }

        LocalDateTime activatedAt = LocalDateTime.now();
        if (receiptFileMapper.transitionState(
                uploadContext.receiptFile().getId(),
                ReceiptFileState.PENDING_SCAN,
                ReceiptFileState.ACTIVE,
                activatedAt
        ) != 1) {
            throw conflict();
        }

        ReceiptFile activated = uploadContext.receiptFile();
        activated.setContentType(validated.contentType());
        activated.setSizeBytes(validated.sizeBytes());
        activated.setSha256Checksum(validated.sha256Checksum());
        activated.setState(ReceiptFileState.ACTIVE);
        activated.setActivatedAt(activatedAt);
        auditLogService.record(
                securityUser,
                currentActive == null
                        ? AuditLogService.ACTION_RECEIPT_UPLOAD
                        : AuditLogService.ACTION_RECEIPT_REPLACE,
                AuditLogService.TARGET_RECEIPT_FILE,
                activated.getId(),
                auditDetail(applicationId, itemId, activated)
        );
        if (currentActive != null) {
            currentActive.setState(ReceiptFileState.PENDING_DELETE);
        }
        return new ActivationResult(toResponse(activated), currentActive);
    }

    private WriteContext loadWriteContext(Long applicationId, Long itemId, SecurityUser securityUser) {
        ExpenseApplication application = expenseApplicationMapper.findByIdForUpdate(applicationId);
        if (application == null) {
            throw notFound("経費申請が見つかりません。");
        }
        ExpenseItem item = expenseItemMapper.findByIdAndApplicationIdForUpdate(itemId, applicationId);
        if (item == null) {
            throw notFound("経費明細が見つかりません。");
        }
        if (!Objects.equals(application.getApplicantId(), securityUser.getId())
                || !application.getStatus().isEditableByApplicant()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この領収書は変更できません。");
        }
        return new WriteContext(application, item);
    }

    private ReceiptFile loadReadableActiveReceipt(
            Long applicationId,
            Long itemId,
            SecurityUser securityUser
    ) {
        ExpenseApplication application = expenseApplicationMapper.findById(applicationId);
        if (application == null) {
            throw notFound("経費申請が見つかりません。");
        }
        ExpenseItem item = expenseItemMapper.findByIdAndApplicationId(itemId, applicationId);
        if (item == null) {
            throw notFound("経費明細が見つかりません。");
        }
        assertReadable(application, securityUser);
        ReceiptFile active = receiptFileMapper.findActiveByExpenseItemId(itemId);
        if (active == null) {
            throw notFound("領収書が見つかりません。");
        }
        return active;
    }

    private void assertReadable(ExpenseApplication application, SecurityUser securityUser) {
        if (Objects.equals(application.getApplicantId(), securityUser.getId())) {
            return;
        }
        if (securityUser.getRole() == RoleType.ADMIN) {
            return;
        }
        if (securityUser.getRole() == RoleType.APPROVER
                && application.getStatus() == ExpenseStatus.SUBMITTED) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この領収書は参照できません。");
    }

    private ReceiptFileResponse toResponse(ReceiptFile receiptFile) {
        ReceiptFileResponse response = new ReceiptFileResponse();
        response.setId(receiptFile.getId());
        response.setOriginalFileName(receiptFile.getOriginalFileName());
        response.setContentType(receiptFile.getContentType());
        response.setSizeBytes(receiptFile.getSizeBytes());
        response.setSha256Checksum(receiptFile.getSha256Checksum());
        response.setUploadedAt(receiptFile.getActivatedAt());
        response.setPreviewAvailable(
                "image/jpeg".equals(receiptFile.getContentType())
                        || "image/png".equals(receiptFile.getContentType())
                        || "application/pdf".equals(receiptFile.getContentType())
        );
        return response;
    }

    private String auditDetail(Long applicationId, Long itemId, ReceiptFile receiptFile) {
        return "applicationId=%d, itemId=%d, sizeBytes=%d, contentType=%s".formatted(
                applicationId,
                itemId,
                receiptFile.getSizeBytes(),
                receiptFile.getContentType()
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "領収書が同時に更新されました。");
    }

    private ReceiptFileException fileServiceUnavailable(Throwable cause) {
        return ReceiptFileException.serviceUnavailable("領収書ファイルサービスを利用できません。", cause);
    }

    private void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // The primary failure is retained.
        }
    }

    private record WriteContext(ExpenseApplication application, ExpenseItem item) {
    }

    private record UploadContext(ReceiptFile receiptFile, Long originalActiveReceiptId) {
    }

    private record ActivationResult(ReceiptFileResponse response, ReceiptFile replacedReceipt) {
    }
}
