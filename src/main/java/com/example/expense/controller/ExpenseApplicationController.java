package com.example.expense.controller;

import com.example.expense.common.ApiResponse;
import com.example.expense.common.PageResponse;
import com.example.expense.dto.request.CreateExpenseApplicationRequest;
import com.example.expense.dto.request.ExpenseApplicationSearchRequest;
import com.example.expense.dto.request.UpdateExpenseApplicationRequest;
import com.example.expense.dto.response.ExpenseApplicationDetailResponse;
import com.example.expense.dto.response.ExpenseApplicationSummaryResponse;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.ExpenseApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expense-applications")
public class ExpenseApplicationController {

    private final ExpenseApplicationService expenseApplicationService;

    public ExpenseApplicationController(ExpenseApplicationService expenseApplicationService) {
        this.expenseApplicationService = expenseApplicationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ExpenseApplicationSummaryResponse>> search(
            @Valid @ModelAttribute ExpenseApplicationSearchRequest request,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(expenseApplicationService.search(request, securityUser));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseApplicationDetailResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(expenseApplicationService.getById(id, securityUser));
    }

    @PostMapping
    public ApiResponse<ExpenseApplicationDetailResponse> create(
            @Valid @RequestBody CreateExpenseApplicationRequest request,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(expenseApplicationService.create(request, securityUser), "経費申請を作成しました。");
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenseApplicationDetailResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateExpenseApplicationRequest request,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(expenseApplicationService.update(id, request, securityUser), "経費申請を更新しました。");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        expenseApplicationService.delete(id, securityUser);
        return ApiResponse.success(null, "経費申請を削除しました。");
    }
}
