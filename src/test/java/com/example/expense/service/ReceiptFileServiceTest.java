package com.example.expense.service;

import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.common.enums.ReceiptFileState;
import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.response.ReceiptFileResponse;
import com.example.expense.entity.ExpenseApplication;
import com.example.expense.entity.ExpenseItem;
import com.example.expense.entity.ReceiptFile;
import com.example.expense.entity.User;
import com.example.expense.repository.ExpenseApplicationMapper;
import com.example.expense.repository.ExpenseItemMapper;
import com.example.expense.repository.ReceiptFileMapper;
import com.example.expense.security.SecurityUser;
import com.example.expense.storage.MalwareScanResult;
import com.example.expense.storage.MalwareScanner;
import com.example.expense.storage.ReceiptStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptFileServiceTest {

    private static final String STORAGE_KEY =
            "receipts/2026/07/10/20/123e4567-e89b-12d3-a456-426614174000";

    @Mock
    private ExpenseApplicationMapper expenseApplicationMapper;

    @Mock
    private ExpenseItemMapper expenseItemMapper;

    @Mock
    private ReceiptFileMapper receiptFileMapper;

    @Mock
    private ReceiptStorage receiptStorage;

    @Mock
    private MalwareScanner malwareScanner;

    @Mock
    private ReceiptFileCleanupService cleanupService;

    @Mock
    private AuditLogService auditLogService;

    private ReceiptFileService service;

    @BeforeEach
    void setUp() {
        ReceiptStorageKeyGenerator keyGenerator = new ReceiptStorageKeyGenerator(
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC),
                () -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        );
        service = new ReceiptFileService(
                expenseApplicationMapper,
                expenseItemMapper,
                receiptFileMapper,
                receiptStorage,
                malwareScanner,
                new ReceiptContentValidator(),
                keyGenerator,
                cleanupService,
                auditLogService,
                new TransactionTemplate(new TestTransactionManager())
        );
    }

    @Test
    void uploadOrReplace_正常系_新規PDFをACTIVEにして監査する() {
        SecurityUser applicant = securityUser(1L, RoleType.USER);
        allowWrite(10L, 20L, applicant);
        when(receiptFileMapper.findActiveByExpenseItemIdForUpdate(20L)).thenReturn(null);
        when(receiptFileMapper.insert(any())).thenAnswer(invocation -> {
            ReceiptFile receipt = invocation.getArgument(0);
            receipt.setId(50L);
            return 1;
        });
        when(receiptFileMapper.markPendingScan(
                eq(50L),
                eq("application/pdf"),
                any(Long.class),
                any(String.class)
        )).thenReturn(1);
        when(malwareScanner.scan(any())).thenReturn(MalwareScanResult.clean());
        when(receiptFileMapper.transitionState(
                eq(50L),
                eq(ReceiptFileState.PENDING_SCAN),
                eq(ReceiptFileState.ACTIVE),
                any()
        )).thenReturn(1);

        ReceiptFileResponse response = service.uploadOrReplace(
                10L,
                20L,
                "receipt.pdf",
                "application/pdf",
                pdf(),
                applicant
        );

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getOriginalFileName()).isEqualTo("receipt.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSha256Checksum()).matches("[0-9a-f]{64}");
        assertThat(response.isPreviewAvailable()).isTrue();
        verify(receiptStorage).put(
                eq(STORAGE_KEY),
                any(),
                eq(16L),
                eq("application/pdf")
        );
        verify(auditLogService).record(
                applicant,
                AuditLogService.ACTION_RECEIPT_UPLOAD,
                AuditLogService.TARGET_RECEIPT_FILE,
                50L,
                "applicationId=10, itemId=20, sizeBytes=16, contentType=application/pdf"
        );
        verify(cleanupService, never()).cleanupBestEffort(any(), any());
    }

    @Test
    void uploadOrReplace_正常系_旧ACTIVEを切り離してreplaceする() {
        SecurityUser applicant = securityUser(1L, RoleType.USER);
        allowWrite(10L, 20L, applicant);
        ReceiptFile oldReceipt = activeReceipt(40L, 20L);
        when(receiptFileMapper.findActiveByExpenseItemIdForUpdate(20L))
                .thenReturn(oldReceipt, oldReceipt);
        when(receiptFileMapper.insert(any())).thenAnswer(invocation -> {
            ReceiptFile receipt = invocation.getArgument(0);
            receipt.setId(50L);
            return 1;
        });
        when(receiptFileMapper.markPendingScan(eq(50L), any(), any(Long.class), any())).thenReturn(1);
        when(malwareScanner.scan(any())).thenReturn(MalwareScanResult.clean());
        when(receiptFileMapper.transitionState(
                eq(40L),
                eq(ReceiptFileState.ACTIVE),
                eq(ReceiptFileState.PENDING_DELETE),
                eq(null)
        )).thenReturn(1);
        when(receiptFileMapper.transitionState(
                eq(50L),
                eq(ReceiptFileState.PENDING_SCAN),
                eq(ReceiptFileState.ACTIVE),
                any()
        )).thenReturn(1);

        ReceiptFileResponse response = service.uploadOrReplace(
                10L,
                20L,
                "replacement.pdf",
                "application/pdf",
                pdf(),
                applicant
        );

        assertThat(response.getId()).isEqualTo(50L);
        verify(auditLogService).record(
                eq(applicant),
                eq(AuditLogService.ACTION_RECEIPT_REPLACE),
                eq(AuditLogService.TARGET_RECEIPT_FILE),
                eq(50L),
                any()
        );
        verify(cleanupService).cleanupBestEffort(40L, oldReceipt.getStorageKey());
    }

    @Test
    void uploadOrReplace_異常系_EICAR判定ならREJECTEDを回収して422() {
        SecurityUser applicant = securityUser(1L, RoleType.USER);
        allowWrite(10L, 20L, applicant);
        ReceiptFile oldReceipt = activeReceipt(40L, 20L);
        when(receiptFileMapper.findActiveByExpenseItemIdForUpdate(20L)).thenReturn(oldReceipt);
        when(receiptFileMapper.insert(any())).thenAnswer(invocation -> {
            ReceiptFile receipt = invocation.getArgument(0);
            receipt.setId(50L);
            return 1;
        });
        when(receiptFileMapper.markPendingScan(eq(50L), any(), any(Long.class), any())).thenReturn(1);
        when(malwareScanner.scan(any())).thenReturn(MalwareScanResult.infected());
        when(receiptFileMapper.transitionState(
                50L,
                ReceiptFileState.PENDING_SCAN,
                ReceiptFileState.REJECTED,
                null
        )).thenReturn(1);

        assertThatThrownBy(() -> service.uploadOrReplace(
                10L,
                20L,
                "receipt.pdf",
                "application/pdf",
                pdf(),
                applicant
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(cleanupService).cleanupBestEffort(50L, STORAGE_KEY);
        verify(receiptFileMapper, never()).transitionState(
                40L,
                ReceiptFileState.ACTIVE,
                ReceiptFileState.PENDING_DELETE,
                null
        );
        verify(auditLogService, never()).record(
                any(),
                eq(AuditLogService.ACTION_RECEIPT_UPLOAD),
                any(),
                any(),
                any()
        );
    }

    @Test
    void uploadOrReplace_異常系_他人はstreamを処理せず403() {
        SecurityUser user = securityUser(2L, RoleType.USER);
        when(expenseApplicationMapper.findByIdForUpdate(10L))
                .thenReturn(application(10L, 1L, ExpenseStatus.DRAFT));
        when(expenseItemMapper.findByIdAndApplicationIdForUpdate(20L, 10L))
                .thenReturn(item(20L, 10L));

        assertThatThrownBy(() -> service.uploadOrReplace(
                10L,
                20L,
                "receipt.pdf",
                "application/pdf",
                pdf(),
                user
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(receiptFileMapper, never()).insert(any());
        verify(receiptStorage, never()).put(any(), any(), any(Long.class), any());
    }

    @Test
    void getMetadata_正常系_APPROVERは他人の申請中を参照できる() {
        SecurityUser approver = securityUser(2L, RoleType.APPROVER);
        when(expenseApplicationMapper.findById(10L))
                .thenReturn(application(10L, 1L, ExpenseStatus.SUBMITTED));
        when(expenseItemMapper.findByIdAndApplicationId(20L, 10L)).thenReturn(item(20L, 10L));
        when(receiptFileMapper.findActiveByExpenseItemId(20L)).thenReturn(activeReceipt(50L, 20L));

        ReceiptFileResponse response = service.getMetadata(10L, 20L, approver);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response).hasNoNullFieldsOrProperties();
    }

    @Test
    void getMetadata_異常系_USERは他人の領収書を参照できない() {
        SecurityUser user = securityUser(2L, RoleType.USER);
        when(expenseApplicationMapper.findById(10L))
                .thenReturn(application(10L, 1L, ExpenseStatus.SUBMITTED));
        when(expenseItemMapper.findByIdAndApplicationId(20L, 10L)).thenReturn(item(20L, 10L));

        assertThatThrownBy(() -> service.getMetadata(10L, 20L, user))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(receiptFileMapper, never()).findActiveByExpenseItemId(20L);
    }

    @Test
    void openContent_異常系_権限外USERはstorageとauditへ到達しない() {
        SecurityUser user = securityUser(2L, RoleType.USER);
        when(expenseApplicationMapper.findById(10L))
                .thenReturn(application(10L, 1L, ExpenseStatus.DRAFT));
        when(expenseItemMapper.findByIdAndApplicationId(20L, 10L)).thenReturn(item(20L, 10L));

        assertThatThrownBy(() -> service.openContent(
                10L,
                20L,
                ReceiptContentDisposition.INLINE,
                user
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(receiptStorage, never()).open(any());
        verify(auditLogService, never()).record(
                any(),
                eq(AuditLogService.ACTION_RECEIPT_PREVIEW),
                any(),
                any(),
                any()
        );
    }

    @Test
    void delete_正常系_ACTIVEをPENDING_DELETEにして監査とcleanupを実行する() {
        SecurityUser applicant = securityUser(1L, RoleType.USER);
        allowWrite(10L, 20L, applicant);
        ReceiptFile receipt = activeReceipt(50L, 20L);
        when(receiptFileMapper.findActiveByExpenseItemIdForUpdate(20L)).thenReturn(receipt);
        when(receiptFileMapper.transitionState(
                50L,
                ReceiptFileState.ACTIVE,
                ReceiptFileState.PENDING_DELETE,
                null
        )).thenReturn(1);

        service.delete(10L, 20L, applicant);

        verify(auditLogService).record(
                eq(applicant),
                eq(AuditLogService.ACTION_RECEIPT_DELETE),
                eq(AuditLogService.TARGET_RECEIPT_FILE),
                eq(50L),
                any()
        );
        verify(cleanupService).cleanupBestEffort(50L, receipt.getStorageKey());
    }

    private void allowWrite(Long applicationId, Long itemId, SecurityUser applicant) {
        when(expenseApplicationMapper.findByIdForUpdate(applicationId))
                .thenReturn(application(applicationId, applicant.getId(), ExpenseStatus.DRAFT));
        when(expenseItemMapper.findByIdAndApplicationIdForUpdate(itemId, applicationId))
                .thenReturn(item(itemId, applicationId));
    }

    private ByteArrayInputStream pdf() {
        return new ByteArrayInputStream("%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII));
    }

    private ExpenseApplication application(Long id, Long applicantId, ExpenseStatus status) {
        ExpenseApplication application = new ExpenseApplication();
        application.setId(id);
        application.setApplicantId(applicantId);
        application.setStatus(status);
        return application;
    }

    private ExpenseItem item(Long id, Long applicationId) {
        ExpenseItem item = new ExpenseItem();
        item.setId(id);
        item.setExpenseApplicationId(applicationId);
        return item;
    }

    private ReceiptFile activeReceipt(Long id, Long itemId) {
        ReceiptFile receipt = new ReceiptFile();
        receipt.setId(id);
        receipt.setExpenseItemId(itemId);
        receipt.setStorageKey("receipts/2026/07/10/20/old");
        receipt.setOriginalFileName("old.pdf");
        receipt.setContentType("application/pdf");
        receipt.setSizeBytes(16L);
        receipt.setSha256Checksum("a".repeat(64));
        receipt.setState(ReceiptFileState.ACTIVE);
        receipt.setUploadedBy(1L);
        receipt.setActivatedAt(java.time.LocalDateTime.of(2026, 7, 23, 10, 0));
        return receipt;
    }

    private SecurityUser securityUser(Long id, RoleType role) {
        User user = new User();
        user.setId(id);
        user.setEmail("user%s@example.com".formatted(id));
        user.setPassword("password");
        user.setName("User " + id);
        user.setRole(role);
        user.setEnabled(true);
        return new SecurityUser(user);
    }
}
