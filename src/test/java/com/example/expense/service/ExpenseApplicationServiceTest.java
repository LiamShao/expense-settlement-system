package com.example.expense.service;

import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.request.ReturnExpenseApplicationRequest;
import com.example.expense.dto.response.ExpenseApplicationDetailResponse;
import com.example.expense.entity.ExpenseApplication;
import com.example.expense.entity.User;
import com.example.expense.repository.ExpenseApplicationMapper;
import com.example.expense.repository.ExpenseItemMapper;
import com.example.expense.repository.UserMapper;
import com.example.expense.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseApplicationServiceTest {

    @Mock
    private ExpenseApplicationMapper expenseApplicationMapper;

    @Mock
    private ExpenseItemMapper expenseItemMapper;

    @Mock
    private UserMapper userMapper;

    private ExpenseApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseApplicationService(expenseApplicationMapper, expenseItemMapper, userMapper);
    }

    @Test
    void submit_正常系_下書きを申請中にする() {
        ExpenseApplication draft = application(10L, 1L, ExpenseStatus.DRAFT);
        ExpenseApplication submitted = application(10L, 1L, ExpenseStatus.SUBMITTED);
        User applicant = user(1L, RoleType.USER);

        when(expenseApplicationMapper.findById(10L)).thenReturn(draft, submitted);
        when(userMapper.findById(1L)).thenReturn(applicant);
        when(expenseItemMapper.findByApplicationId(10L)).thenReturn(List.of());

        ExpenseApplicationDetailResponse response = service.submit(10L, new SecurityUser(applicant));

        assertThat(response.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        verify(expenseApplicationMapper).updateStatusToSubmitted(10L);
    }

    @Test
    void approve_正常系_承認者が申請中を承認する() {
        ExpenseApplication submitted = application(10L, 1L, ExpenseStatus.SUBMITTED);
        ExpenseApplication approved = application(10L, 1L, ExpenseStatus.APPROVED);
        approved.setApprovedBy(2L);
        User applicant = user(1L, RoleType.USER);
        User approver = user(2L, RoleType.APPROVER);

        when(expenseApplicationMapper.findById(10L)).thenReturn(submitted, approved);
        when(userMapper.findById(1L)).thenReturn(applicant);
        when(userMapper.findById(2L)).thenReturn(approver);
        when(expenseItemMapper.findByApplicationId(10L)).thenReturn(List.of());

        ExpenseApplicationDetailResponse response = service.approve(10L, new SecurityUser(approver));

        assertThat(response.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(response.getApprover().getId()).isEqualTo(2L);
        verify(expenseApplicationMapper).updateStatusToApproved(10L, 2L);
    }

    @Test
    void approve_異常系_自分の申請は承認できない() {
        ExpenseApplication submitted = application(10L, 2L, ExpenseStatus.SUBMITTED);
        User approver = user(2L, RoleType.APPROVER);

        when(expenseApplicationMapper.findById(10L)).thenReturn(submitted);

        assertThatThrownBy(() -> service.approve(10L, new SecurityUser(approver)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(expenseApplicationMapper, never()).updateStatusToApproved(10L, 2L);
    }

    @Test
    void returnApplication_異常系_USERは差戻しできない() {
        ExpenseApplication submitted = application(10L, 2L, ExpenseStatus.SUBMITTED);
        User user = user(1L, RoleType.USER);
        ReturnExpenseApplicationRequest request = new ReturnExpenseApplicationRequest();
        request.setReturnReason("領収書を確認できません。");

        when(expenseApplicationMapper.findById(10L)).thenReturn(submitted);

        assertThatThrownBy(() -> service.returnApplication(10L, request, new SecurityUser(user)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(expenseApplicationMapper, never()).updateStatusToReturned(10L, 1L, request.getReturnReason());
    }

    private ExpenseApplication application(Long id, Long applicantId, ExpenseStatus status) {
        ExpenseApplication application = new ExpenseApplication();
        application.setId(id);
        application.setApplicantId(applicantId);
        application.setTitle("出張交通費");
        application.setStatus(status);
        application.setTotalAmount(BigDecimal.valueOf(1200));
        return application;
    }

    private User user(Long id, RoleType role) {
        User user = new User();
        user.setId(id);
        user.setEmployeeCode("E%03d".formatted(id));
        user.setName("テストユーザー" + id);
        user.setEmail("user%s@example.com".formatted(id));
        user.setPassword("password");
        user.setRole(role);
        user.setDepartment("開発部");
        user.setEnabled(true);
        return user;
    }
}
