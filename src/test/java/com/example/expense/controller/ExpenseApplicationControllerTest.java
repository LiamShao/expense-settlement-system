package com.example.expense.controller;

import com.example.expense.common.GlobalExceptionHandler;
import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.common.enums.RoleType;
import com.example.expense.config.SecurityConfig;
import com.example.expense.dto.response.ExpenseApplicationDetailResponse;
import com.example.expense.dto.response.UserResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class ExpenseApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseApplicationService expenseApplicationService;

    @Test
    void create_正常系_作成した経費申請を返す() throws Exception {
        ExpenseApplicationDetailResponse response = new ExpenseApplicationDetailResponse();
        response.setId(10L);
        response.setApplicant(userResponse());
        response.setTitle("東京出張交通費");
        response.setStatus(ExpenseStatus.DRAFT);
        response.setStatusName(ExpenseStatus.DRAFT.getDisplayName());
        response.setTotalAmount(BigDecimal.valueOf(1200));
        response.setItems(List.of());

        when(expenseApplicationService.create(argThat(request ->
                request.getTitle().equals("東京出張交通費")
                        && request.getItems().size() == 1
                        && request.getItems().get(0).getAmount().compareTo(BigDecimal.valueOf(1200)) == 0
        ), any(SecurityUser.class))).thenReturn(response);

        mockMvc.perform(post("/api/expense-applications")
                        .with(user(securityUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "東京出張交通費",
                                  "items": [
                                    {
                                      "expenseDate": "2026-07-13",
                                      "category": "TRANSPORTATION",
                                      "amount": 1200,
                                      "description": "電車代"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("経費申請を作成しました。"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalAmount").value(1200));

        verify(expenseApplicationService).create(any(), argThat(principal -> principal.getId().equals(1L)));
    }

    @Test
    void create_異常系_金額の小数は許可しない() throws Exception {
        mockMvc.perform(post("/api/expense-applications")
                        .with(user(securityUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "東京出張交通費",
                                  "items": [
                                    {
                                      "expenseDate": "2026-07-13",
                                      "category": "TRANSPORTATION",
                                      "amount": 1200.5,
                                      "description": "電車代"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("items[0].amount"));

        verifyNoInteractions(expenseApplicationService);
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

    private UserResponse userResponse() {
        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setEmployeeCode("E001");
        response.setName("テストユーザー");
        response.setEmail("user@example.com");
        response.setRole(RoleType.USER);
        response.setRoleName(RoleType.USER.getDisplayName());
        return response;
    }
}
