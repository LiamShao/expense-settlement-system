package com.example.expense.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticationType").value("Basic"))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.data.user.role").value("USER"));
    }

    @Test
    void me_結合テスト_Basic認証ユーザーをDBから取得する() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic(USER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeCode").value("E0001"))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"));
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

        mockMvc.perform(get("/api/expense-applications").with(httpBasic(USER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].applicantId").value(1));

        mockMvc.perform(get("/api/expense-applications").with(httpBasic(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void workflow_結合テスト_作成から申請と承認まで遷移する() throws Exception {
        long applicationId = createApplication("承認フロー結合テスト", 3000, 2000);

        submitApplication(applicationId);

        mockMvc.perform(post("/api/expense-applications/{id}/approve", applicationId)
                        .with(httpBasic(APPROVER_EMAIL, PASSWORD)))
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

        mockMvc.perform(get("/api/expense-applications/{id}", applicationId)
                        .with(httpBasic(USER_EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void auditLog_結合テスト_ADMINは業務操作ログを検索できる() throws Exception {
        long applicationId = createApplication("監査ログ結合テスト", 500, 700);
        submitApplication(applicationId);

        mockMvc.perform(post("/api/expense-applications/{id}/approve", applicationId)
                        .with(httpBasic(APPROVER_EMAIL, PASSWORD)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE target_id = ?", Long.class, applicationId
        )).isEqualTo(3L);

        mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic(ADMIN_EMAIL, PASSWORD))
                        .param("action", "EXPENSE_APPLICATION_APPROVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(2))
                .andExpect(jsonPath("$.data.content[0].targetId").value(applicationId));
    }

    @Test
    void auditLog_結合テスト_USERは監査ログを参照できない() throws Exception {
        mockMvc.perform(get("/api/audit-logs").with(httpBasic(USER_EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private long createApplication(String title, int firstAmount, int secondAmount) throws Exception {
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
                        .with(httpBasic(USER_EMAIL, PASSWORD))
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
        mockMvc.perform(post("/api/expense-applications/{id}/submit", applicationId)
                        .with(httpBasic(USER_EMAIL, PASSWORD)))
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
}
