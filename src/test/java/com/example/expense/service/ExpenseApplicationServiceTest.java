package com.example.expense.service;

import com.example.expense.common.enums.ExpenseCategory;
import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.request.ExpenseItemRequest;
import com.example.expense.dto.request.ExpenseApplicationSearchRequest;
import com.example.expense.dto.request.ReturnExpenseApplicationRequest;
import com.example.expense.dto.request.UpdateExpenseApplicationRequest;
import com.example.expense.dto.response.ExpenseApplicationDetailResponse;
import com.example.expense.dto.response.ExpenseApplicationSummaryResponse;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private AuditLogService auditLogService;

    private ExpenseApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseApplicationService(expenseApplicationMapper, expenseItemMapper, userMapper, auditLogService);
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
        verify(auditLogService).record(
                any(SecurityUser.class),
                eq(AuditLogService.ACTION_EXPENSE_APPLICATION_SUBMIT),
                eq(AuditLogService.TARGET_EXPENSE_APPLICATION),
                eq(10L),
                eq("経費申請を申請しました。")
        );
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
    void search_正常系_ADMINは全件検索できる() {
        User admin = user(3L, RoleType.ADMIN);
        ExpenseApplicationSummaryResponse summary = new ExpenseApplicationSummaryResponse();
        summary.setId(10L);
        summary.setApplicantId(1L);
        summary.setStatus(ExpenseStatus.SUBMITTED);

        when(expenseApplicationMapper.search(any())).thenReturn(List.of(summary));
        when(expenseApplicationMapper.countSearch(any())).thenReturn(1L);

        var response = service.search(new ExpenseApplicationSearchRequest(), new SecurityUser(admin));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getStatusName()).isEqualTo(ExpenseStatus.SUBMITTED.getDisplayName());
        verify(expenseApplicationMapper).search(org.mockito.ArgumentMatchers.argThat(condition -> condition.getApplicantId() == null));
    }

    @Test
    void getById_正常系_ADMINは他人の申請詳細を参照できる() {
        ExpenseApplication application = application(10L, 1L, ExpenseStatus.SUBMITTED);
        User applicant = user(1L, RoleType.USER);
        User admin = user(3L, RoleType.ADMIN);

        when(expenseApplicationMapper.findById(10L)).thenReturn(application);
        when(userMapper.findById(1L)).thenReturn(applicant);
        when(expenseItemMapper.findByApplicationId(10L)).thenReturn(List.of());

        ExpenseApplicationDetailResponse response = service.getById(10L, new SecurityUser(admin));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getApplicant().getId()).isEqualTo(1L);
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

    @Test
    void update_正常系_下書きのヘッダと明細を更新する() {
        ExpenseApplication draft = application(10L, 1L, ExpenseStatus.DRAFT);
        User applicant = user(1L, RoleType.USER);
        UpdateExpenseApplicationRequest request = updateRequest();

        when(expenseApplicationMapper.findById(10L)).thenReturn(draft);
        when(userMapper.findById(1L)).thenReturn(applicant);
        when(expenseItemMapper.findByApplicationId(10L)).thenReturn(List.of());

        ExpenseApplicationDetailResponse response = service.update(10L, request, new SecurityUser(applicant));

        assertThat(response.getTitle()).isEqualTo("更新後の出張交通費");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("2500");
        verify(expenseApplicationMapper).updateDraft(org.mockito.ArgumentMatchers.argThat(application ->
                application.getId().equals(10L)
                        && application.getTitle().equals("更新後の出張交通費")
                        && application.getTotalAmount().compareTo(BigDecimal.valueOf(2500)) == 0
        ));
        verify(expenseItemMapper).deleteByApplicationId(10L);
        verify(expenseItemMapper).insertBatch(org.mockito.ArgumentMatchers.argThat(items ->
                items.size() == 1
                        && items.get(0).getExpenseApplicationId().equals(10L)
                        && items.get(0).getAmount().compareTo(BigDecimal.valueOf(2500)) == 0
        ));
    }

    @Test
    void update_異常系_申請中は更新できない() {
        ExpenseApplication submitted = application(10L, 1L, ExpenseStatus.SUBMITTED);
        User applicant = user(1L, RoleType.USER);

        when(expenseApplicationMapper.findById(10L)).thenReturn(submitted);

        assertThatThrownBy(() -> service.update(10L, updateRequest(), new SecurityUser(applicant)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(expenseApplicationMapper, never()).updateDraft(any());
        verify(expenseItemMapper, never()).deleteByApplicationId(any());
        verify(expenseItemMapper, never()).insertBatch(any());
    }

    @Test
    void getById_異常系_他人の申請は参照できない() {
        ExpenseApplication application = application(10L, 2L, ExpenseStatus.DRAFT);
        User user = user(1L, RoleType.USER);

        when(expenseApplicationMapper.findById(10L)).thenReturn(application);

        assertThatThrownBy(() -> service.getById(10L, new SecurityUser(user)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(userMapper, never()).findById(any());
        verify(expenseItemMapper, never()).findByApplicationId(any());
    }

    @Test
    void approve_異常系_下書きは承認できない() {
        ExpenseApplication draft = application(10L, 1L, ExpenseStatus.DRAFT);
        User approver = user(2L, RoleType.APPROVER);

        when(expenseApplicationMapper.findById(10L)).thenReturn(draft);

        assertThatThrownBy(() -> service.approve(10L, new SecurityUser(approver)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(expenseApplicationMapper, never()).updateStatusToApproved(any(), any());
    }

    @Test
    void returnApplication_正常系_承認者が申請中を差戻す() {
        ExpenseApplication submitted = application(10L, 1L, ExpenseStatus.SUBMITTED);
        ExpenseApplication returned = application(10L, 1L, ExpenseStatus.RETURNED);
        returned.setApprovedBy(2L);
        returned.setReturnReason("領収書を確認できません。");
        User applicant = user(1L, RoleType.USER);
        User approver = user(2L, RoleType.APPROVER);
        ReturnExpenseApplicationRequest request = new ReturnExpenseApplicationRequest();
        request.setReturnReason("領収書を確認できません。");

        when(expenseApplicationMapper.findById(10L)).thenReturn(submitted, returned);
        when(userMapper.findById(1L)).thenReturn(applicant);
        when(userMapper.findById(2L)).thenReturn(approver);
        when(expenseItemMapper.findByApplicationId(10L)).thenReturn(List.of());

        ExpenseApplicationDetailResponse response = service.returnApplication(10L, request, new SecurityUser(approver));

        assertThat(response.getStatus()).isEqualTo(ExpenseStatus.RETURNED);
        assertThat(response.getReturnReason()).isEqualTo("領収書を確認できません。");
        verify(expenseApplicationMapper).updateStatusToReturned(10L, 2L, "領収書を確認できません。");
        verify(auditLogService).record(
                any(SecurityUser.class),
                eq(AuditLogService.ACTION_EXPENSE_APPLICATION_RETURN),
                eq(AuditLogService.TARGET_EXPENSE_APPLICATION),
                eq(10L),
                eq("領収書を確認できません。")
        );
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

    private UpdateExpenseApplicationRequest updateRequest() {
        ExpenseItemRequest item = new ExpenseItemRequest();
        item.setExpenseDate(LocalDate.of(2026, 7, 13));
        item.setCategory(ExpenseCategory.TRANSPORTATION);
        item.setAmount(BigDecimal.valueOf(2500));
        item.setDescription("新幹線代");

        UpdateExpenseApplicationRequest request = new UpdateExpenseApplicationRequest();
        request.setTitle("更新後の出張交通費");
        request.setItems(List.of(item));
        return request;
    }
}
