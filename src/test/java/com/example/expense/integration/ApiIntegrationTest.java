package com.example.expense.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
