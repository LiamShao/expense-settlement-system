package com.example.expense.repository;

import com.example.expense.entity.ExpenseItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExpenseItemMapper {

    List<ExpenseItem> findByApplicationId(@Param("expenseApplicationId") Long expenseApplicationId);

    List<ExpenseItem> findByApplicationIdForUpdate(@Param("expenseApplicationId") Long expenseApplicationId);

    ExpenseItem findByIdAndApplicationId(
            @Param("id") Long id,
            @Param("expenseApplicationId") Long expenseApplicationId
    );

    ExpenseItem findByIdAndApplicationIdForUpdate(
            @Param("id") Long id,
            @Param("expenseApplicationId") Long expenseApplicationId
    );

    int insert(ExpenseItem expenseItem);

    int insertBatch(@Param("items") List<ExpenseItem> items);

    int update(ExpenseItem expenseItem);

    int deleteByIdAndApplicationId(
            @Param("id") Long id,
            @Param("expenseApplicationId") Long expenseApplicationId
    );

    int deleteByApplicationId(@Param("expenseApplicationId") Long expenseApplicationId);
}
