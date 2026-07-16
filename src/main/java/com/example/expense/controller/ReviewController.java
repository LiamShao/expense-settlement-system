package com.example.expense.controller;

import com.example.expense.common.ApiResponse;
import com.example.expense.common.PageResponse;
import com.example.expense.dto.request.ReviewSearchRequest;
import com.example.expense.dto.response.ExpenseApplicationDetailResponse;
import com.example.expense.dto.response.ExpenseApplicationSummaryResponse;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.ExpenseApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ExpenseApplicationService expenseApplicationService;

    public ReviewController(ExpenseApplicationService expenseApplicationService) {
        this.expenseApplicationService = expenseApplicationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ExpenseApplicationSummaryResponse>> search(
            @Valid @ModelAttribute ReviewSearchRequest request,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(expenseApplicationService.searchReviews(request, securityUser));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseApplicationDetailResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(expenseApplicationService.getReviewById(id, securityUser));
    }
}
