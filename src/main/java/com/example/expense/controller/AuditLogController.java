package com.example.expense.controller;

import com.example.expense.common.ApiResponse;
import com.example.expense.common.PageResponse;
import com.example.expense.dto.request.AuditLogSearchRequest;
import com.example.expense.dto.response.AuditLogResponse;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> search(
            @Valid @ModelAttribute AuditLogSearchRequest request,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(auditLogService.search(request, securityUser));
    }
}
