package com.example.expense.repository;

import com.example.expense.entity.ExpenseItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExpenseItemMapper {

    List<ExpenseItem> findByApplicationId(@Param("expenseApplicationId") Long expenseApplicationId);

    int insert(ExpenseItem expenseItem);

    int insertBatch(@Param("items") List<ExpenseItem> items);

    int deleteByApplicationId(@Param("expenseApplicationId") Long expenseApplicationId);
}
