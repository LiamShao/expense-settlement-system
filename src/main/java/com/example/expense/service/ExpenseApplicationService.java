package com.example.expense.service;

import com.example.expense.common.PageResponse;
import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.request.CreateExpenseApplicationRequest;
import com.example.expense.dto.request.ExpenseApplicationSearchRequest;
import com.example.expense.dto.request.ExpenseItemRequest;
import com.example.expense.dto.request.ReturnExpenseApplicationRequest;
import com.example.expense.dto.request.ReviewSearchRequest;
import com.example.expense.dto.request.UpdateExpenseApplicationRequest;
import com.example.expense.dto.request.UpdateExpenseItemRequest;
import com.example.expense.dto.response.ExpenseApplicationDetailResponse;
import com.example.expense.dto.response.ExpenseApplicationSummaryResponse;
import com.example.expense.dto.response.ExpenseItemResponse;
import com.example.expense.dto.response.UserResponse;
import com.example.expense.entity.ExpenseApplication;
import com.example.expense.entity.ExpenseItem;
import com.example.expense.entity.User;
import com.example.expense.repository.ExpenseApplicationMapper;
import com.example.expense.repository.ExpenseItemMapper;
import com.example.expense.repository.ReceiptFileMapper;
import com.example.expense.repository.UserMapper;
import com.example.expense.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ExpenseApplicationService {

    static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("999999999999");

    private final ExpenseApplicationMapper expenseApplicationMapper;
    private final ExpenseItemMapper expenseItemMapper;
    private final ReceiptFileMapper receiptFileMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    public ExpenseApplicationService(
            ExpenseApplicationMapper expenseApplicationMapper,
            ExpenseItemMapper expenseItemMapper,
            ReceiptFileMapper receiptFileMapper,
            UserMapper userMapper,
            AuditLogService auditLogService
    ) {
        this.expenseApplicationMapper = expenseApplicationMapper;
        this.expenseItemMapper = expenseItemMapper;
        this.receiptFileMapper = receiptFileMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
    }

    public PageResponse<ExpenseApplicationSummaryResponse> search(
            ExpenseApplicationSearchRequest request,
            SecurityUser securityUser
    ) {
        ExpenseApplicationSearchRequest condition = copySearchCondition(request);
        if (isAdmin(securityUser)) {
            return searchByCondition(condition);
        }
        if (condition.getApplicantId() != null && !Objects.equals(condition.getApplicantId(), securityUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの経費申請は参照できません。");
        }
        condition.setApplicantId(securityUser.getId());

        return searchByCondition(condition);
    }

    private PageResponse<ExpenseApplicationSummaryResponse> searchByCondition(ExpenseApplicationSearchRequest condition) {
        List<ExpenseApplicationSummaryResponse> content = expenseApplicationMapper.search(condition);
        content.forEach(this::setStatusName);
        long totalElements = expenseApplicationMapper.countSearch(condition);
        return new PageResponse<>(content, condition.getPage(), condition.getSize(), totalElements);
    }

    public ExpenseApplicationDetailResponse getById(Long id, SecurityUser securityUser) {
        ExpenseApplication application = findApplication(id);
        assertOwnerOrAdmin(application, securityUser);
        return toDetailResponse(application);
    }

    public PageResponse<ExpenseApplicationSummaryResponse> searchReviews(
            ReviewSearchRequest request,
            SecurityUser securityUser
    ) {
        assertReviewer(securityUser);
        List<ExpenseApplicationSummaryResponse> content = expenseApplicationMapper.searchReviews(
                request,
                securityUser.getId()
        );
        content.forEach(this::setStatusName);
        long totalElements = expenseApplicationMapper.countReviews(request, securityUser.getId());
        return new PageResponse<>(content, request.getPage(), request.getSize(), totalElements);
    }

    public ExpenseApplicationDetailResponse getReviewById(Long id, SecurityUser securityUser) {
        assertReviewer(securityUser);
        ExpenseApplication application = findApplication(id);
        assertReviewable(application);
        assertNotOwnApplication(application, securityUser);
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
        auditLogService.record(
                securityUser,
                AuditLogService.ACTION_EXPENSE_APPLICATION_CREATE,
                AuditLogService.TARGET_EXPENSE_APPLICATION,
                application.getId(),
                "経費申請を作成しました。"
        );

        return getById(application.getId(), securityUser);
    }

    @Transactional
    public ExpenseApplicationDetailResponse update(
            Long id,
            UpdateExpenseApplicationRequest request,
            SecurityUser securityUser
    ) {
        ExpenseApplication application = findApplicationForUpdate(id);
        assertOwner(application, securityUser);
        assertEditable(application);

        application.setTitle(request.getTitle());
        application.setTotalAmount(calculateTotalAmount(request.getItems()));
        expenseApplicationMapper.updateDraft(application);

        reconcileExpenseItems(application.getId(), request.getItems());
        auditLogService.record(
                securityUser,
                AuditLogService.ACTION_EXPENSE_APPLICATION_UPDATE,
                AuditLogService.TARGET_EXPENSE_APPLICATION,
                application.getId(),
                "経費申請を更新しました。"
        );

        return getById(application.getId(), securityUser);
    }

    @Transactional
    public void delete(Long id, SecurityUser securityUser) {
        ExpenseApplication application = findApplicationForUpdate(id);
        assertOwner(application, securityUser);
        assertEditable(application);

        if (receiptFileMapper.existsByApplicationId(application.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "領収書を削除してから経費申請を削除してください。"
            );
        }
        expenseApplicationMapper.deleteById(application.getId());
        auditLogService.record(
                securityUser,
                AuditLogService.ACTION_EXPENSE_APPLICATION_DELETE,
                AuditLogService.TARGET_EXPENSE_APPLICATION,
                application.getId(),
                "経費申請を削除しました。"
        );
    }

    @Transactional
    public ExpenseApplicationDetailResponse submit(Long id, SecurityUser securityUser) {
        ExpenseApplication application = findApplication(id);
        assertOwner(application, securityUser);
        assertSubmittable(application);

        expenseApplicationMapper.updateStatusToSubmitted(application.getId());
        auditLogService.record(
                securityUser,
                AuditLogService.ACTION_EXPENSE_APPLICATION_SUBMIT,
                AuditLogService.TARGET_EXPENSE_APPLICATION,
                application.getId(),
                "経費申請を申請しました。"
        );
        return toDetailResponse(findApplication(application.getId()));
    }

    @Transactional
    public ExpenseApplicationDetailResponse approve(Long id, SecurityUser securityUser) {
        ExpenseApplication application = findApplication(id);
        assertReviewer(securityUser);
        assertReviewable(application);
        assertNotOwnApplication(application, securityUser);

        expenseApplicationMapper.updateStatusToApproved(application.getId(), securityUser.getId());
        auditLogService.record(
                securityUser,
                AuditLogService.ACTION_EXPENSE_APPLICATION_APPROVE,
                AuditLogService.TARGET_EXPENSE_APPLICATION,
                application.getId(),
                "経費申請を承認しました。"
        );
        return toDetailResponse(findApplication(application.getId()));
    }

    @Transactional
    public ExpenseApplicationDetailResponse returnApplication(
            Long id,
            ReturnExpenseApplicationRequest request,
            SecurityUser securityUser
    ) {
        ExpenseApplication application = findApplication(id);
        assertReviewer(securityUser);
        assertReviewable(application);
        assertNotOwnApplication(application, securityUser);

        expenseApplicationMapper.updateStatusToReturned(
                application.getId(),
                securityUser.getId(),
                request.getReturnReason()
        );
        auditLogService.record(
                securityUser,
                AuditLogService.ACTION_EXPENSE_APPLICATION_RETURN,
                AuditLogService.TARGET_EXPENSE_APPLICATION,
                application.getId(),
                request.getReturnReason()
        );
        return toDetailResponse(findApplication(application.getId()));
    }

    private ExpenseApplication findApplication(Long id) {
        ExpenseApplication application = expenseApplicationMapper.findById(id);
        if (application == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "経費申請が見つかりません。");
        }
        return application;
    }

    private ExpenseApplication findApplicationForUpdate(Long id) {
        ExpenseApplication application = expenseApplicationMapper.findByIdForUpdate(id);
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

    private void assertOwnerOrAdmin(ExpenseApplication application, SecurityUser securityUser) {
        if (!isAdmin(securityUser)) {
            assertOwner(application, securityUser);
        }
    }

    private boolean isAdmin(SecurityUser securityUser) {
        return securityUser.getUser().getRole() == RoleType.ADMIN;
    }

    private void assertEditable(ExpenseApplication application) {
        if (!application.getStatus().isEditableByApplicant()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "下書きまたは差戻しの経費申請のみ編集できます。");
        }
    }

    private void assertSubmittable(ExpenseApplication application) {
        if (!application.getStatus().isEditableByApplicant()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "下書きまたは差戻しの経費申請のみ申請できます。");
        }
    }

    private void assertReviewer(SecurityUser securityUser) {
        RoleType role = securityUser.getUser().getRole();
        if (role != RoleType.APPROVER && role != RoleType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "承認者または管理者のみ承認・差戻しできます。");
        }
    }

    private void assertReviewable(ExpenseApplication application) {
        if (!application.getStatus().isReviewable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申請中の経費申請のみ承認・差戻しできます。");
        }
    }

    private void assertNotOwnApplication(ExpenseApplication application, SecurityUser securityUser) {
        if (Objects.equals(application.getApplicantId(), securityUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分の経費申請は承認・差戻しできません。");
        }
    }

    private BigDecimal calculateTotalAmount(List<? extends ExpenseItemRequest> items) {
        BigDecimal totalAmount = items.stream()
                .map(ExpenseItemRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "経費申請の合計金額は999999999999円以下にしてください。"
            );
        }
        return totalAmount;
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
                    return item;
                })
                .toList();
    }

    private void reconcileExpenseItems(Long applicationId, List<UpdateExpenseItemRequest> requests) {
        List<ExpenseItem> existingItems = expenseItemMapper.findByApplicationIdForUpdate(applicationId);
        Map<Long, ExpenseItem> existingById = new HashMap<>();
        for (ExpenseItem existingItem : existingItems) {
            existingById.put(existingItem.getId(), existingItem);
        }

        Set<Long> retainedIds = new HashSet<>();
        for (UpdateExpenseItemRequest request : requests) {
            if (request.getId() == null) {
                expenseItemMapper.insert(toExpenseItem(applicationId, request));
                continue;
            }
            if (!retainedIds.add(request.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同じ経費明細 ID が重複しています。");
            }
            ExpenseItem existingItem = existingById.get(request.getId());
            if (existingItem == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "経費明細が対象の経費申請に属していません。");
            }
            applyEditableFields(existingItem, request);
            if (expenseItemMapper.update(existingItem) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "経費明細が同時に更新されました。");
            }
        }

        for (ExpenseItem existingItem : existingItems) {
            if (retainedIds.contains(existingItem.getId())) {
                continue;
            }
            if (receiptFileMapper.existsByExpenseItemId(existingItem.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "領収書を削除してから経費明細を削除してください。"
                );
            }
            if (expenseItemMapper.deleteByIdAndApplicationId(existingItem.getId(), applicationId) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "経費明細が同時に更新されました。");
            }
        }
    }

    private ExpenseItem toExpenseItem(Long applicationId, ExpenseItemRequest request) {
        ExpenseItem item = new ExpenseItem();
        item.setExpenseApplicationId(applicationId);
        applyEditableFields(item, request);
        return item;
    }

    private void applyEditableFields(ExpenseItem item, ExpenseItemRequest request) {
        item.setExpenseDate(request.getExpenseDate());
        item.setCategory(request.getCategory());
        item.setAmount(request.getAmount());
        item.setDescription(request.getDescription());
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
