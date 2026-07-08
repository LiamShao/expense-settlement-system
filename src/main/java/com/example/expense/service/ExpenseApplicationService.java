package com.example.expense.service;

import com.example.expense.common.PageResponse;
import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.dto.request.CreateExpenseApplicationRequest;
import com.example.expense.dto.request.ExpenseApplicationSearchRequest;
import com.example.expense.dto.request.ExpenseItemRequest;
import com.example.expense.dto.request.UpdateExpenseApplicationRequest;
import com.example.expense.dto.response.ExpenseApplicationDetailResponse;
import com.example.expense.dto.response.ExpenseApplicationSummaryResponse;
import com.example.expense.dto.response.ExpenseItemResponse;
import com.example.expense.dto.response.UserResponse;
import com.example.expense.entity.ExpenseApplication;
import com.example.expense.entity.ExpenseItem;
import com.example.expense.entity.User;
import com.example.expense.repository.ExpenseApplicationMapper;
import com.example.expense.repository.ExpenseItemMapper;
import com.example.expense.repository.UserMapper;
import com.example.expense.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class ExpenseApplicationService {

    private final ExpenseApplicationMapper expenseApplicationMapper;
    private final ExpenseItemMapper expenseItemMapper;
    private final UserMapper userMapper;

    public ExpenseApplicationService(
            ExpenseApplicationMapper expenseApplicationMapper,
            ExpenseItemMapper expenseItemMapper,
            UserMapper userMapper
    ) {
        this.expenseApplicationMapper = expenseApplicationMapper;
        this.expenseItemMapper = expenseItemMapper;
        this.userMapper = userMapper;
    }

    public PageResponse<ExpenseApplicationSummaryResponse> search(
            ExpenseApplicationSearchRequest request,
            SecurityUser securityUser
    ) {
        ExpenseApplicationSearchRequest condition = copySearchCondition(request);
        if (condition.getApplicantId() != null && !Objects.equals(condition.getApplicantId(), securityUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの経費申請は参照できません。");
        }
        condition.setApplicantId(securityUser.getId());

        List<ExpenseApplicationSummaryResponse> content = expenseApplicationMapper.search(condition);
        content.forEach(this::setStatusName);
        long totalElements = expenseApplicationMapper.countSearch(condition);
        return new PageResponse<>(content, condition.getPage(), condition.getSize(), totalElements);
    }

    public ExpenseApplicationDetailResponse getById(Long id, SecurityUser securityUser) {
        ExpenseApplication application = findApplication(id);
        assertOwner(application, securityUser);
        return toDetailResponse(application);
    }

    @Transactional
    public ExpenseApplicationDetailResponse create(
            CreateExpenseApplicationRequest request,
            SecurityUser securityUser
    ) {
        ExpenseApplication application = new ExpenseApplication();
        application.setApplicantId(securityUser.getId());
        application.setTitle(request.getTitle());
        application.setStatus(ExpenseStatus.DRAFT);
        application.setTotalAmount(calculateTotalAmount(request.getItems()));

        expenseApplicationMapper.insert(application);
        expenseItemMapper.insertBatch(toExpenseItems(application.getId(), request.getItems()));

        return getById(application.getId(), securityUser);
    }

    @Transactional
    public ExpenseApplicationDetailResponse update(
            Long id,
            UpdateExpenseApplicationRequest request,
            SecurityUser securityUser
    ) {
        ExpenseApplication application = findApplication(id);
        assertOwner(application, securityUser);
        assertEditable(application);

        application.setTitle(request.getTitle());
        application.setTotalAmount(calculateTotalAmount(request.getItems()));
        expenseApplicationMapper.updateDraft(application);

        expenseItemMapper.deleteByApplicationId(application.getId());
        expenseItemMapper.insertBatch(toExpenseItems(application.getId(), request.getItems()));

        return getById(application.getId(), securityUser);
    }

    @Transactional
    public void delete(Long id, SecurityUser securityUser) {
        ExpenseApplication application = findApplication(id);
        assertOwner(application, securityUser);
        assertEditable(application);

        expenseApplicationMapper.deleteById(application.getId());
    }

    private ExpenseApplication findApplication(Long id) {
        ExpenseApplication application = expenseApplicationMapper.findById(id);
        if (application == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "経費申請が見つかりません。");
        }
        return application;
    }

    private void assertOwner(ExpenseApplication application, SecurityUser securityUser) {
        if (!Objects.equals(application.getApplicantId(), securityUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの経費申請は操作できません。");
        }
    }

    private void assertEditable(ExpenseApplication application) {
        if (!application.getStatus().isEditableByApplicant()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "下書きまたは差戻しの経費申請のみ編集できます。");
        }
    }

    private BigDecimal calculateTotalAmount(List<ExpenseItemRequest> items) {
        return items.stream()
                .map(ExpenseItemRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ExpenseItem> toExpenseItems(Long applicationId, List<ExpenseItemRequest> requests) {
        return requests.stream()
                .map(request -> {
                    ExpenseItem item = new ExpenseItem();
                    item.setExpenseApplicationId(applicationId);
                    item.setExpenseDate(request.getExpenseDate());
                    item.setCategory(request.getCategory());
                    item.setAmount(request.getAmount());
                    item.setDescription(request.getDescription());
                    item.setReceiptObjectKey(request.getReceiptObjectKey());
                    return item;
                })
                .toList();
    }

    private ExpenseApplicationDetailResponse toDetailResponse(ExpenseApplication application) {
        User applicant = userMapper.findById(application.getApplicantId());
        User approver = application.getApprovedBy() == null ? null : userMapper.findById(application.getApprovedBy());
        List<ExpenseItemResponse> items = expenseItemMapper.findByApplicationId(application.getId()).stream()
                .map(this::toExpenseItemResponse)
                .toList();

        ExpenseApplicationDetailResponse response = new ExpenseApplicationDetailResponse();
        response.setId(application.getId());
        response.setApplicant(toUserResponse(applicant));
        response.setTitle(application.getTitle());
        response.setStatus(application.getStatus());
        response.setStatusName(application.getStatus().getDisplayName());
        response.setTotalAmount(application.getTotalAmount());
        response.setSubmittedAt(application.getSubmittedAt());
        response.setApprovedAt(application.getApprovedAt());
        response.setApprover(toUserResponse(approver));
        response.setReturnedAt(application.getReturnedAt());
        response.setReturnReason(application.getReturnReason());
        response.setItems(items);
        response.setCreatedAt(application.getCreatedAt());
        response.setUpdatedAt(application.getUpdatedAt());
        return response;
    }

    private ExpenseItemResponse toExpenseItemResponse(ExpenseItem item) {
        ExpenseItemResponse response = new ExpenseItemResponse();
        response.setId(item.getId());
        response.setExpenseDate(item.getExpenseDate());
        response.setCategory(item.getCategory());
        response.setCategoryName(item.getCategory().getDisplayName());
        response.setAmount(item.getAmount());
        response.setDescription(item.getDescription());
        response.setReceiptObjectKey(item.getReceiptObjectKey());
        return response;
    }

    private UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }

        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmployeeCode(user.getEmployeeCode());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setRoleName(user.getRole().getDisplayName());
        response.setDepartment(user.getDepartment());
        return response;
    }

    private void setStatusName(ExpenseApplicationSummaryResponse response) {
        if (response.getStatus() != null) {
            response.setStatusName(response.getStatus().getDisplayName());
        }
    }

    private ExpenseApplicationSearchRequest copySearchCondition(ExpenseApplicationSearchRequest source) {
        ExpenseApplicationSearchRequest condition = new ExpenseApplicationSearchRequest();
        condition.setApplicantId(source.getApplicantId());
        condition.setStatus(source.getStatus());
        condition.setKeyword(source.getKeyword());
        condition.setExpenseDateFrom(source.getExpenseDateFrom());
        condition.setExpenseDateTo(source.getExpenseDateTo());
        condition.setPage(source.getPage());
        condition.setSize(source.getSize());
        return condition;
    }
}
