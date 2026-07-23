package com.example.expense.repository;

import com.example.expense.common.enums.ReceiptFileState;
import com.example.expense.entity.ReceiptFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReceiptFileMapper {

    ReceiptFile findById(@Param("id") Long id);

    ReceiptFile findByIdForUpdate(@Param("id") Long id);

    ReceiptFile findActiveByExpenseItemId(@Param("expenseItemId") Long expenseItemId);

    ReceiptFile findActiveByExpenseItemIdForUpdate(@Param("expenseItemId") Long expenseItemId);

    boolean existsByExpenseItemId(@Param("expenseItemId") Long expenseItemId);

    boolean existsByApplicationId(@Param("expenseApplicationId") Long expenseApplicationId);

    List<ReceiptFile> findStaleByStates(
            @Param("states") List<ReceiptFileState> states,
            @Param("updatedBefore") LocalDateTime updatedBefore,
            @Param("limit") int limit
    );

    int insert(ReceiptFile receiptFile);

    int markPendingScan(
            @Param("id") Long id,
            @Param("contentType") String contentType,
            @Param("sizeBytes") long sizeBytes,
            @Param("sha256Checksum") String sha256Checksum
    );

    int transitionState(
            @Param("id") Long id,
            @Param("expectedState") ReceiptFileState expectedState,
            @Param("newState") ReceiptFileState newState,
            @Param("activatedAt") LocalDateTime activatedAt
    );

    int deleteByIdAndState(
            @Param("id") Long id,
            @Param("state") ReceiptFileState state
    );
}
