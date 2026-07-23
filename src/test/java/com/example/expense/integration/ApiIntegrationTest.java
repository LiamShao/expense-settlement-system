package com.example.expense.integration;

import com.example.expense.common.enums.ReceiptFileState;
import com.example.expense.dto.response.ReceiptFileResponse;
import com.example.expense.entity.ReceiptFile;
import com.example.expense.repository.ReceiptFileMapper;
import com.example.expense.repository.UserMapper;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.ReceiptContentValidator;
import com.example.expense.service.ReceiptFileService;
import com.example.expense.storage.ReceiptStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ApiIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String USER_EMAIL = "user@example.com";
    private static final String APPROVER_EMAIL = "approver@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final Path RECEIPT_STORAGE_ROOT = createReceiptStorageRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("expense_test")
            .withUsername("expense_test")
            .withPassword("expense_test");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.receipt.storage.type", () -> "local");
        registry.add("app.receipt.storage.local-root", RECEIPT_STORAGE_ROOT::toString);
        registry.add("app.receipt.scanner.type", () -> "eicar-test");
    }

    @AfterAll
    static void deleteReceiptStorageRoot() throws IOException {
        try (var paths = Files.walk(RECEIPT_STORAGE_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReceiptFileMapper receiptFileMapper;

    @Autowired
    private ReceiptFileService receiptFileService;

    @Autowired
    private ReceiptStorage receiptStorage;

    @Autowired
    private UserMapper userMapper;

    @Test
    void login_結合テスト_DBユーザーでログインできる() throws Exception {
        BrowserSession session = login(USER_EMAIL);

        assertThat(session.cookie().isHttpOnly()).isTrue();
        assertThat(session.loginSetCookie()).contains("SameSite=Lax");
        assertThat(session.cookie().getValue()).isNotEqualTo(session.anonymousSessionId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spring_session WHERE principal_name = ?",
                Long.class,
                USER_EMAIL
        )).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void me_結合テスト_Session認証ユーザーをDBから取得する() throws Exception {
        BrowserSession session = login(USER_EMAIL);

        mockMvc.perform(get("/api/auth/me").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeCode").value("E0001"))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic(USER_EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_結合テスト_旧Sessionを再利用できない() throws Exception {
        BrowserSession session = login(USER_EMAIL);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ログアウトしました。"));

        mockMvc.perform(get("/api/auth/me").cookie(session.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void login_結合テスト_5回失敗でAccountを一時Lockする() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie anonymousCookie = requireSessionCookie(csrfResult);
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray())
                .path("data")
                .path("token")
                .asText();

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .cookie(anonymousCookie)
                            .header("X-CSRF-TOKEN", csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "user@example.com",
                                      "password": "wrong-password"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE email = ?",
                Integer.class,
                USER_EMAIL
        )).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT locked_until > CURRENT_TIMESTAMP FROM users WHERE email = ?",
                Boolean.class,
                USER_EMAIL
        )).isTrue();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(anonymousCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void create_結合テスト_CSRF不足では更新しない() throws Exception {
        BrowserSession session = login(USER_EMAIL);
        long before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expense_applications",
                Long.class
        );

        mockMvc.perform(post("/api/expense-applications")
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "CSRF failure",
                                  "items": [
                                    {
                                      "expenseDate": "2026-07-20",
                                      "category": "MEAL",
                                      "amount": 1000,
                                      "description": "test"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expense_applications",
                Long.class
        )).isEqualTo(before);
    }

    @Test
    void create_結合テスト_申請と明細と監査ログをDBへ保存する() throws Exception {
        long applicationId = createApplication("結合テスト交通費", 1200, 800);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM expense_applications WHERE id = ?", String.class, applicationId
        )).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_amount FROM expense_applications WHERE id = ?", Long.class, applicationId
        )).isEqualTo(2000L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expense_items WHERE expense_application_id = ?", Long.class, applicationId
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action = 'EXPENSE_APPLICATION_CREATE'",
                Long.class,
                applicationId
        )).isEqualTo(1L);
    }

    @Test
    void receiptFile_結合テスト_V5SchemaとMapperでlifecycleを管理する() {
        ReceiptFile receiptFile = new ReceiptFile();
        receiptFile.setExpenseItemId(1L);
        receiptFile.setStorageKey("receipts/2026/07/1/1/integration-test");
        receiptFile.setOriginalFileName("receipt.pdf");
        receiptFile.setState(ReceiptFileState.UPLOADING);
        receiptFile.setUploadedBy(1L);

        assertThat(receiptFileMapper.insert(receiptFile)).isEqualTo(1);
        assertThat(receiptFile.getId()).isNotNull();
        assertThat(receiptFileMapper.findActiveByExpenseItemId(1L)).isNull();

        String checksum = "a".repeat(64);
        assertThat(receiptFileMapper.markPendingScan(
                receiptFile.getId(),
                "application/pdf",
                1024L,
                checksum
        )).isEqualTo(1);

        ReceiptFile pending = receiptFileMapper.findByIdForUpdate(receiptFile.getId());
        assertThat(pending.getState()).isEqualTo(ReceiptFileState.PENDING_SCAN);
        assertThat(pending.getContentType()).isEqualTo("application/pdf");
        assertThat(pending.getSizeBytes()).isEqualTo(1024L);
        assertThat(pending.getSha256Checksum()).isEqualTo(checksum);

        assertThat(receiptFileMapper.transitionState(
                receiptFile.getId(),
                ReceiptFileState.PENDING_SCAN,
                ReceiptFileState.ACTIVE,
                LocalDateTime.now()
        )).isEqualTo(1);

        ReceiptFile active = receiptFileMapper.findActiveByExpenseItemIdForUpdate(1L);
        assertThat(active).isNotNull();
        assertThat(active.getId()).isEqualTo(receiptFile.getId());
        assertThat(active.getActivatedAt()).isNotNull();

        assertThat(receiptFileMapper.transitionState(
                receiptFile.getId(),
                ReceiptFileState.ACTIVE,
                ReceiptFileState.PENDING_DELETE,
                null
        )).isEqualTo(1);
        assertThat(receiptFileMapper.findActiveByExpenseItemId(1L)).isNull();
        assertThat(receiptFileMapper.findStaleByStates(
                List.of(ReceiptFileState.PENDING_DELETE),
                LocalDateTime.now().plusMinutes(1),
                10
        )).extracting(ReceiptFile::getId).contains(receiptFile.getId());
        assertThat(receiptFileMapper.findStaleByStates(
                List.of(),
                LocalDateTime.now().plusMinutes(1),
                10
        )).isEmpty();
        assertThat(receiptFileMapper.deleteByIdAndState(
                receiptFile.getId(),
                ReceiptFileState.PENDING_DELETE
        )).isEqualTo(1);
        assertThat(receiptFileMapper.findById(receiptFile.getId())).isNull();
    }

    @Test
    void receiptFileService_結合テスト_実DBとLocalStorageでuploadReplaceDeleteする() throws Exception {
        long applicationId = createApplication("領収書service結合テスト", 1200, 800);
        Long itemId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM expense_items
                        WHERE expense_application_id = ?
                        ORDER BY id
                        LIMIT 1
                        """,
                Long.class,
                applicationId
        );
        SecurityUser applicant = new SecurityUser(userMapper.findById(1L));

        ReceiptFileResponse uploaded = receiptFileService.uploadOrReplace(
                applicationId,
                itemId,
                "first.pdf",
                "application/pdf",
                new ByteArrayInputStream("%PDF-1.7\nfirst".getBytes(StandardCharsets.US_ASCII)),
                applicant
        );
        ReceiptFile first = receiptFileMapper.findById(uploaded.getId());
        assertThat(first.getState()).isEqualTo(ReceiptFileState.ACTIVE);
        assertThat(receiptStorage.exists(first.getStorageKey())).isTrue();

        ReceiptFileResponse replaced = receiptFileService.uploadOrReplace(
                applicationId,
                itemId,
                "second.pdf",
                "application/pdf",
                new ByteArrayInputStream("%PDF-1.7\nsecond".getBytes(StandardCharsets.US_ASCII)),
                applicant
        );
        ReceiptFile second = receiptFileMapper.findById(replaced.getId());
        assertThat(second.getState()).isEqualTo(ReceiptFileState.ACTIVE);
        assertThat(receiptFileMapper.findById(uploaded.getId())).isNull();
        assertThat(receiptStorage.exists(first.getStorageKey())).isFalse();
        assertThat(receiptStorage.exists(second.getStorageKey())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM audit_logs
                        WHERE target_type = 'RECEIPT_FILE'
                          AND action IN ('RECEIPT_UPLOAD', 'RECEIPT_REPLACE')
                        """,
                Long.class
        )).isEqualTo(2L);

        receiptFileService.delete(applicationId, itemId, applicant);

        assertThat(receiptFileMapper.findById(replaced.getId())).isNull();
        assertThat(receiptStorage.exists(second.getStorageKey())).isFalse();
    }

    @Test
    void receiptFileApi_結合テスト_multipartからmetadataContentDeleteまで実行する() throws Exception {
        long applicationId = createApplication("領収書API結合テスト", 1200, 800);
        Long itemId = findItemIds(applicationId).get(0);
        BrowserSession session = login(USER_EMAIL);
        String path = receiptPath(applicationId, itemId);
        byte[] pdf = "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII);

        mockMvc.perform(multipart(path)
                        .file(new MockMultipartFile("file", "領収書.pdf", "application/pdf", pdf))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFileName").value("領収書.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.sizeBytes").value(pdf.length))
                .andExpect(jsonPath("$.data.sha256Checksum").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.data.previewAvailable").value(true))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());

        mockMvc.perform(get(path).cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFileName").value("領収書.pdf"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());

        MvcResult started = mockMvc.perform(get(path + "/content")
                        .cookie(session.cookie())
                        .param("disposition", "inline"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("inline"),
                                org.hamcrest.Matchers.containsString("filename*=UTF-8''")
                        )
                ));

        mockMvc.perform(delete(path)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("領収書を削除しました。"));

        mockMvc.perform(get(path).cookie(session.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM audit_logs
                        WHERE target_type = 'RECEIPT_FILE'
                          AND action IN ('RECEIPT_UPLOAD', 'RECEIPT_PREVIEW', 'RECEIPT_DELETE')
                        """,
                Long.class
        )).isEqualTo(3L);
    }

    @Test
    void receiptFileApi_結合テスト_専用validationCodeと旧ACTIVE保護を確認する() throws Exception {
        long applicationId = createApplication("領収書validation結合テスト", 1200, 800);
        List<Long> itemIds = findItemIds(applicationId);
        BrowserSession session = login(USER_EMAIL);
        String activePath = receiptPath(applicationId, itemIds.get(0));
        String emptyPath = receiptPath(applicationId, itemIds.get(1));
        byte[] cleanPdf = "%PDF-1.7\nclean".getBytes(StandardCharsets.US_ASCII);

        mockMvc.perform(multipart(activePath)
                        .file(new MockMultipartFile("file", "clean.pdf", "application/pdf", cleanPdf))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isOk());
        Long originalReceiptId = receiptFileMapper.findActiveByExpenseItemId(itemIds.get(0)).getId();

        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$"
                + "EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        mockMvc.perform(multipart(activePath)
                        .file(new MockMultipartFile(
                                "file",
                                "infected.pdf",
                                "application/pdf",
                                ("%PDF-1.7\n" + eicar).getBytes(StandardCharsets.US_ASCII)
                        ))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MALWARE_DETECTED"));
        assertThat(receiptFileMapper.findActiveByExpenseItemId(itemIds.get(0)).getId())
                .isEqualTo(originalReceiptId);

        mockMvc.perform(multipart(emptyPath)
                        .file(new MockMultipartFile(
                                "file",
                                "fake.pdf",
                                "application/pdf",
                                "<html>".getBytes(StandardCharsets.US_ASCII)
                        ))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        mockMvc.perform(multipart(emptyPath)
                        .file(new MockMultipartFile(
                                "file",
                                "receipt.svg",
                                "image/svg+xml",
                                "<svg/>".getBytes(StandardCharsets.US_ASCII)
                        ))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        assertThat(receiptFileMapper.findActiveByExpenseItemId(itemIds.get(1))).isNull();
    }

    @Test
    void receiptFileApi_結合テスト_reviewとadminのread境界を適用する() throws Exception {
        long applicationId = createApplication("領収書read認可結合テスト", 1200, 800);
        Long itemId = findItemIds(applicationId).get(0);
        BrowserSession owner = login(USER_EMAIL);
        BrowserSession approver = login(APPROVER_EMAIL);
        BrowserSession admin = login(ADMIN_EMAIL);
        String path = receiptPath(applicationId, itemId);

        mockMvc.perform(multipart(path)
                        .file(new MockMultipartFile(
                                "file",
                                "receipt.pdf",
                                "application/pdf",
                                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII)
                        ))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get(path).cookie(approver.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(path).cookie(admin.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/expense-applications/{id}/submit", applicationId)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get(path).cookie(approver.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(delete(path)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void receiptFileApi_結合テスト_10MiB境界と超過を専用codeで処理する() throws Exception {
        long applicationId = createApplication("領収書size境界結合テスト", 1200, 800);
        List<Long> itemIds = findItemIds(applicationId);
        BrowserSession session = login(USER_EMAIL);
        byte[] boundary = pdfBytes((int) ReceiptContentValidator.MAX_SIZE_BYTES);

        mockMvc.perform(multipart(receiptPath(applicationId, itemIds.get(0)))
                        .file(new MockMultipartFile("file", "boundary.pdf", "application/pdf", boundary))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeBytes").value(ReceiptContentValidator.MAX_SIZE_BYTES));

        byte[] overLimit = pdfBytes((int) ReceiptContentValidator.MAX_SIZE_BYTES + 1);
        mockMvc.perform(multipart(receiptPath(applicationId, itemIds.get(1)))
                        .file(new MockMultipartFile("file", "large.pdf", "application/pdf", overLimit))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(receiptFileMapper.findActiveByExpenseItemId(itemIds.get(1))).isNull();
    }

    @Test
    void update_結合テスト_既存明細IDを維持して差分更新する() throws Exception {
        long applicationId = createApplication("明細reconcile結合テスト", 1200, 800);
        List<Long> originalIds = jdbcTemplate.queryForList(
                """
                        SELECT id
                        FROM expense_items
                        WHERE expense_application_id = ?
                        ORDER BY id
                        """,
                Long.class,
                applicationId
        );
        BrowserSession session = login(USER_EMAIL);

        mockMvc.perform(put("/api/expense-applications/{id}", applicationId)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "明細reconcile更新後",
                                  "items": [
                                    {
                                      "id": %d,
                                      "expenseDate": "2026-07-12",
                                      "category": "TRANSPORTATION",
                                      "amount": 1500,
                                      "description": "更新した電車代"
                                    },
                                    {
                                      "expenseDate": "2026-07-13",
                                      "category": "SUPPLIES",
                                      "amount": 500,
                                      "description": "新規備品"
                                    }
                                  ]
                                }
                                """.formatted(originalIds.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(2000))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        List<Long> reconciledIds = jdbcTemplate.queryForList(
                """
                        SELECT id
                        FROM expense_items
                        WHERE expense_application_id = ?
                        ORDER BY id
                        """,
                Long.class,
                applicationId
        );
        assertThat(reconciledIds)
                .contains(originalIds.get(0))
                .doesNotContain(originalIds.get(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount FROM expense_items WHERE id = ?",
                Long.class,
                originalIds.get(0)
        )).isEqualTo(1500L);
    }

    @Test
    void search_結合テスト_USERは本人のみADMINは全件参照できる() throws Exception {
        insertApplicationForApprover();
        BrowserSession userSession = login(USER_EMAIL);
        BrowserSession adminSession = login(ADMIN_EMAIL);

        mockMvc.perform(get("/api/expense-applications").cookie(userSession.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].applicantId").value(1));

        mockMvc.perform(get("/api/expense-applications").cookie(adminSession.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void workflow_結合テスト_作成から申請と承認まで遷移する() throws Exception {
        long applicationId = createApplication("承認フロー結合テスト", 3000, 2000);

        submitApplication(applicationId);
        BrowserSession approverSession = login(APPROVER_EMAIL);

        mockMvc.perform(post("/api/expense-applications/{id}/approve", applicationId)
                        .cookie(approverSession.cookie())
                        .header("X-CSRF-TOKEN", approverSession.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approver.id").value(2));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM expense_applications WHERE id = ?", String.class, applicationId
        )).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approved_by FROM expense_applications WHERE id = ?", Long.class, applicationId
        )).isEqualTo(2L);
    }

    @Test
    void getById_結合テスト_USERは他人の申請を参照できない() throws Exception {
        long applicationId = insertApplicationForApprover();
        BrowserSession userSession = login(USER_EMAIL);

        mockMvc.perform(get("/api/expense-applications/{id}", applicationId)
                        .cookie(userSession.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void review_結合テスト_APPROVERは他人の申請中だけ参照できる() throws Exception {
        long submittedId = createApplication("承認待ち検索テスト", 1200, 800);
        submitApplication(submittedId);
        long ownApplicationId = insertApplicationForApprover();
        jdbcTemplate.update(
                "UPDATE expense_applications SET status = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP WHERE id = ?",
                ownApplicationId
        );
        BrowserSession approverSession = login(APPROVER_EMAIL);
        BrowserSession userSession = login(USER_EMAIL);

        mockMvc.perform(get("/api/reviews")
                        .cookie(approverSession.cookie())
                        .param("keyword", "承認待ち"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(submittedId))
                .andExpect(jsonPath("$.data.content[0].applicantId").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("SUBMITTED"));

        mockMvc.perform(get("/api/reviews/{id}", submittedId)
                        .cookie(approverSession.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(submittedId))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get("/api/reviews/{id}", ownApplicationId)
                        .cookie(approverSession.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/reviews").cookie(userSession.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void auditLog_結合テスト_ADMINは業務操作ログを検索できる() throws Exception {
        long applicationId = createApplication("監査ログ結合テスト", 500, 700);
        submitApplication(applicationId);
        BrowserSession approverSession = login(APPROVER_EMAIL);
        BrowserSession adminSession = login(ADMIN_EMAIL);

        mockMvc.perform(post("/api/expense-applications/{id}/approve", applicationId)
                        .cookie(approverSession.cookie())
                        .header("X-CSRF-TOKEN", approverSession.csrfToken()))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE target_id = ?", Long.class, applicationId
        )).isEqualTo(3L);

        mockMvc.perform(get("/api/audit-logs")
                        .cookie(adminSession.cookie())
                        .param("action", "EXPENSE_APPLICATION_APPROVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(2))
                .andExpect(jsonPath("$.data.content[0].targetId").value(applicationId));
    }

    @Test
    void auditLog_結合テスト_USERは監査ログを参照できない() throws Exception {
        BrowserSession userSession = login(USER_EMAIL);
        mockMvc.perform(get("/api/audit-logs").cookie(userSession.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private long createApplication(String title, int firstAmount, int secondAmount) throws Exception {
        BrowserSession userSession = login(USER_EMAIL);
        String request = """
                {
                  "title": "%s",
                  "items": [
                    {
                      "expenseDate": "2026-07-10",
                      "category": "TRANSPORTATION",
                      "amount": %d,
                      "description": "電車代"
                    },
                    {
                      "expenseDate": "2026-07-11",
                      "category": "MEAL",
                      "amount": %d,
                      "description": "出張時食事代"
                    }
                  ]
                }
                """.formatted(title, firstAmount, secondAmount);

        MvcResult result = mockMvc.perform(post("/api/expense-applications")
                        .cookie(userSession.cookie())
                        .header("X-CSRF-TOKEN", userSession.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalAmount").value(firstAmount + secondAmount))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("id").asLong();
    }

    private void submitApplication(long applicationId) throws Exception {
        BrowserSession userSession = login(USER_EMAIL);
        mockMvc.perform(post("/api/expense-applications/{id}/submit", applicationId)
                        .cookie(userSession.cookie())
                        .header("X-CSRF-TOKEN", userSession.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    private List<Long> findItemIds(long applicationId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT id
                        FROM expense_items
                        WHERE expense_application_id = ?
                        ORDER BY id
                        """,
                Long.class,
                applicationId
        );
    }

    private String receiptPath(long applicationId, long itemId) {
        return "/api/expense-applications/%d/items/%d/receipt".formatted(applicationId, itemId);
    }

    private byte[] pdfBytes(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    private long insertApplicationForApprover() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO expense_applications (applicant_id, title, status, total_amount)
                VALUES (2, '承認者本人の申請', 'DRAFT', 1000)
                RETURNING id
                """, Long.class);
    }

    private BrowserSession login(String email) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        Cookie anonymousCookie = requireSessionCookie(csrfResult);
        JsonNode csrfResponse = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
        String csrfToken = csrfResponse.path("data").path("token").asText();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(anonymousCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticationType").value("Session"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andReturn();

        return new BrowserSession(
                requireSessionCookie(loginResult),
                csrfToken,
                anonymousCookie.getValue(),
                loginResult.getResponse().getHeader("Set-Cookie")
        );
    }

    private static Path createReceiptStorageRoot() {
        try {
            return Files.createTempDirectory("receipt-integration-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private Cookie requireSessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("SESSION cookie").isNotNull();
        return cookie;
    }

    private record BrowserSession(
            Cookie cookie,
            String csrfToken,
            String anonymousSessionId,
            String loginSetCookie
    ) {
    }
}
