package com.example.expense.controller;

import com.example.expense.common.GlobalExceptionHandler;
import com.example.expense.common.PageResponse;
import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.common.enums.RoleType;
import com.example.expense.config.SecurityConfig;
import com.example.expense.dto.response.ExpenseApplicationSummaryResponse;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseApplicationService expenseApplicationService;

    @Test
    void search_正常系_承認待ち申請を返す() throws Exception {
        ExpenseApplicationSummaryResponse summary = new ExpenseApplicationSummaryResponse();
        summary.setId(10L);
        summary.setApplicantId(1L);
        summary.setApplicantName("山田 太郎");
        summary.setTitle("東京出張交通費");
        summary.setStatus(ExpenseStatus.SUBMITTED);
        summary.setStatusName("申請中");
        summary.setTotalAmount(BigDecimal.valueOf(1200));

        when(expenseApplicationService.searchReviews(
                argThat(request -> request.getKeyword().equals("東京") && request.getPage() == 0),
                argThat(principal -> principal.getId().equals(2L))
        )).thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1));

        mockMvc.perform(get("/api/reviews")
                        .with(user(securityUser()))
                        .param("keyword", "東京"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.content[0].status").value("SUBMITTED"));
    }

    private SecurityUser securityUser() {
        User user = new User();
        user.setId(2L);
        user.setEmployeeCode("E002");
        user.setName("承認者");
        user.setEmail("approver@example.com");
        user.setPassword("password");
        user.setRole(RoleType.APPROVER);
        user.setEnabled(true);
        return new SecurityUser(user);
    }
}
