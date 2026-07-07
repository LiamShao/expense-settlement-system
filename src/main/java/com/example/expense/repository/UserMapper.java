package com.example.expense.repository;

import com.example.expense.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User findById(@Param("id") Long id);

    User findByEmail(@Param("email") String email);

    List<User> findAllEnabled();
}
