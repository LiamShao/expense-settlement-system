package com.example.expense.controller;

import com.example.expense.common.GlobalExceptionHandler;
import com.example.expense.common.PageResponse;
import com.example.expense.common.enums.RoleType;
import com.example.expense.config.SecurityConfig;
import com.example.expense.dto.response.AuditLogResponse;
import com.example.expense.entity.User;
import com.example.expense.security.RestAccessDeniedHandler;
import com.example.expense.security.RestAuthenticationEntryPoint;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    void search_正常系_検索条件と監査ログ一覧を返す() throws Exception {
        AuditLogResponse log = new AuditLogResponse();
        log.setId(100L);
        log.setUserId(1L);
        log.setUserName("テストユーザー");
        log.setAction(AuditLogService.ACTION_EXPENSE_APPLICATION_SUBMIT);
        log.setTargetType(AuditLogService.TARGET_EXPENSE_APPLICATION);
        log.setTargetId(10L);
        log.setDetail("経費申請を申請しました。");
        log.setCreatedAt(LocalDateTime.of(2026, 7, 13, 10, 0));

        when(auditLogService.search(argThat(request ->
                request.getUserId().equals(1L)
                        && request.getAction().equals(AuditLogService.ACTION_EXPENSE_APPLICATION_SUBMIT)
                        && request.getPage() == 0
                        && request.getSize() == 10
        ), any(SecurityUser.class))).thenReturn(new PageResponse<>(List.of(log), 0, 10, 1));

        mockMvc.perform(get("/api/audit-logs")
                        .with(user(securityUser()))
                        .param("userId", "1")
                        .param("action", AuditLogService.ACTION_EXPENSE_APPLICATION_SUBMIT)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(100))
                .andExpect(jsonPath("$.data.content[0].action").value(AuditLogService.ACTION_EXPENSE_APPLICATION_SUBMIT))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(auditLogService).search(any(), argThat(principal -> principal.getId().equals(3L)));
    }

    private SecurityUser securityUser() {
        User user = new User();
        user.setId(3L);
        user.setEmployeeCode("E003");
        user.setName("管理者");
        user.setEmail("admin@example.com");
        user.setPassword("password");
        user.setRole(RoleType.ADMIN);
        user.setEnabled(true);
        return new SecurityUser(user);
    }
}
