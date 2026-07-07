package com.example.expense.repository;

import com.example.expense.common.enums.ExpenseStatus;
import com.example.expense.dto.request.ExpenseApplicationSearchRequest;
import com.example.expense.dto.response.ExpenseApplicationSummaryResponse;
import com.example.expense.entity.ExpenseApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ExpenseApplicationMapper {

    ExpenseApplication findById(@Param("id") Long id);

    List<ExpenseApplicationSummaryResponse> search(@Param("condition") ExpenseApplicationSearchRequest condition);

    long countSearch(@Param("condition") ExpenseApplicationSearchRequest condition);

    int insert(ExpenseApplication expenseApplication);

    int updateDraft(ExpenseApplication expenseApplication);

    int updateTotalAmount(@Param("id") Long id, @Param("totalAmount") BigDecimal totalAmount);

    int updateStatusToSubmitted(@Param("id") Long id);

    int updateStatusToApproved(@Param("id") Long id, @Param("approverId") Long approverId);

    int updateStatusToReturned(@Param("id") Long id, @Param("returnReason") String returnReason);

    int deleteById(@Param("id") Long id);

    boolean existsByIdAndStatus(@Param("id") Long id, @Param("status") ExpenseStatus status);
}
