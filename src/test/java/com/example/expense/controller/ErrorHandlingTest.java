package com.example.expense.controller;

import com.example.expense.common.GlobalExceptionHandler;
import com.example.expense.common.enums.RoleType;
import com.example.expense.config.SecurityConfig;
import com.example.expense.entity.User;
import com.example.expense.security.RestAccessDeniedHandler;
import com.example.expense.security.RestAuthenticationEntryPoint;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.ExpenseApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseApplicationController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class ErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseApplicationService expenseApplicationService;

    @Test
    void create_異常系_Validationエラーを統一形式で返す() throws Exception {
        mockMvc.perform(post("/api/expense-applications")
                        .with(user(securityUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("入力内容に誤りがあります。"))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.path").value("/api/expense-applications"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(expenseApplicationService, never()).create(any(), any());
    }

    @Test
    void create_異常系_不正なJSONを統一形式で返す() throws Exception {
        mockMvc.perform(post("/api/expense-applications")
                        .with(user(securityUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/expense-applications"));
    }

    @Test
    void getById_異常系_ResponseStatusExceptionを統一形式で返す() throws Exception {
        when(expenseApplicationService.getById(eq(999L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "経費申請が見つかりません。"));

        mockMvc.perform(get("/api/expense-applications/999")
                        .with(user(securityUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("経費申請が見つかりません。"))
                .andExpect(jsonPath("$.path").value("/api/expense-applications/999"));
    }

    @Test
    void getById_異常系_未処理例外の詳細を公開しない() throws Exception {
        when(expenseApplicationService.getById(eq(1L), any()))
                .thenThrow(new IllegalStateException("database password must not be exposed"));

        mockMvc.perform(get("/api/expense-applications/1")
                        .with(user(securityUser())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("システムエラーが発生しました。"));
    }

    @Test
    void search_異常系_未認証は統一形式で返す() throws Exception {
        mockMvc.perform(get("/api/expense-applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("認証が必要です。"))
                .andExpect(jsonPath("$.path").value("/api/expense-applications"));
    }

    private SecurityUser securityUser() {
        User user = new User();
        user.setId(1L);
        user.setEmployeeCode("E001");
        user.setName("テストユーザー");
        user.setEmail("user@example.com");
        user.setPassword("password");
        user.setRole(RoleType.USER);
        user.setEnabled(true);
        return new SecurityUser(user);
    }
}
