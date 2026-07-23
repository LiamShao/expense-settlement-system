package com.example.expense.service;

import com.example.expense.common.PageResponse;
import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.request.AuditLogSearchRequest;
import com.example.expense.dto.response.AuditLogResponse;
import com.example.expense.entity.AuditLog;
import com.example.expense.repository.AuditLogMapper;
import com.example.expense.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AuditLogService {

    public static final String TARGET_EXPENSE_APPLICATION = "EXPENSE_APPLICATION";
    public static final String ACTION_EXPENSE_APPLICATION_CREATE = "EXPENSE_APPLICATION_CREATE";
    public static final String ACTION_EXPENSE_APPLICATION_UPDATE = "EXPENSE_APPLICATION_UPDATE";
    public static final String ACTION_EXPENSE_APPLICATION_DELETE = "EXPENSE_APPLICATION_DELETE";
    public static final String ACTION_EXPENSE_APPLICATION_SUBMIT = "EXPENSE_APPLICATION_SUBMIT";
    public static final String ACTION_EXPENSE_APPLICATION_APPROVE = "EXPENSE_APPLICATION_APPROVE";
    public static final String ACTION_EXPENSE_APPLICATION_RETURN = "EXPENSE_APPLICATION_RETURN";
    public static final String TARGET_RECEIPT_FILE = "RECEIPT_FILE";
    public static final String ACTION_RECEIPT_UPLOAD = "RECEIPT_UPLOAD";
    public static final String ACTION_RECEIPT_REPLACE = "RECEIPT_REPLACE";
    public static final String ACTION_RECEIPT_DELETE = "RECEIPT_DELETE";
    public static final String ACTION_RECEIPT_PREVIEW = "RECEIPT_PREVIEW";
    public static final String ACTION_RECEIPT_DOWNLOAD = "RECEIPT_DOWNLOAD";

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public PageResponse<AuditLogResponse> search(
            AuditLogSearchRequest request,
            SecurityUser securityUser
    ) {
        assertAdmin(securityUser);

        AuditLogSearchRequest condition = copySearchCondition(request);
        List<AuditLogResponse> content = auditLogMapper.search(condition);
        long totalElements = auditLogMapper.countSearch(condition);
        return new PageResponse<>(content, condition.getPage(), condition.getSize(), totalElements);
    }

    public void record(
            SecurityUser securityUser,
            String action,
            String targetType,
            Long targetId,
            String detail
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(securityUser.getId());
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLogMapper.insert(auditLog);
    }

    private void assertAdmin(SecurityUser securityUser) {
        if (securityUser.getUser().getRole() != RoleType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理者のみ監査ログを参照できます。");
        }
    }

    private AuditLogSearchRequest copySearchCondition(AuditLogSearchRequest source) {
        AuditLogSearchRequest condition = new AuditLogSearchRequest();
        condition.setUserId(source.getUserId());
        condition.setAction(source.getAction());
        condition.setTargetType(source.getTargetType());
        condition.setCreatedDateFrom(source.getCreatedDateFrom());
        condition.setCreatedDateTo(source.getCreatedDateTo());
        condition.setPage(source.getPage());
        condition.setSize(source.getSize());
        return condition;
    }
}
