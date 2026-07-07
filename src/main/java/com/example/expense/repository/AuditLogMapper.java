package com.example.expense.repository;

import com.example.expense.dto.request.AuditLogSearchRequest;
import com.example.expense.dto.response.AuditLogResponse;
import com.example.expense.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLog auditLog);

    List<AuditLogResponse> search(@Param("condition") AuditLogSearchRequest condition);

    long countSearch(@Param("condition") AuditLogSearchRequest condition);
}
