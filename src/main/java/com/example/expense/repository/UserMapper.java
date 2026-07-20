package com.example.expense.repository;

import com.example.expense.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    User findById(@Param("id") Long id);

    User findByEmail(@Param("email") String email);

    List<User> findAllEnabled();

    int recordLoginFailure(
            @Param("id") Long id,
            @Param("maxFailedAttempts") int maxFailedAttempts,
            @Param("lockedUntil") LocalDateTime lockedUntil
    );

    int recordLoginSuccess(@Param("id") Long id);
}
