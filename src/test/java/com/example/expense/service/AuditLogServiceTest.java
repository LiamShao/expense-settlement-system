package com.example.expense.service;

import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.request.AuditLogSearchRequest;
import com.example.expense.dto.response.AuditLogResponse;
import com.example.expense.entity.User;
import com.example.expense.repository.AuditLogMapper;
import com.example.expense.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogMapper);
    }

    @Test
    void search_正常系_ADMINは監査ログを検索できる() {
        User admin = user(3L, RoleType.ADMIN);
        AuditLogResponse auditLog = new AuditLogResponse();
        auditLog.setId(100L);
        auditLog.setAction(AuditLogService.ACTION_EXPENSE_APPLICATION_SUBMIT);

        when(auditLogMapper.search(any())).thenReturn(List.of(auditLog));
        when(auditLogMapper.countSearch(any())).thenReturn(1L);

        var response = service.search(new AuditLogSearchRequest(), new SecurityUser(admin));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1L);
    }

    @Test
    void search_異常系_USERは監査ログを検索できない() {
        User user = user(1L, RoleType.USER);

        assertThatThrownBy(() -> service.search(new AuditLogSearchRequest(), new SecurityUser(user)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(auditLogMapper, never()).search(any());
    }

    private User user(Long id, RoleType role) {
        User user = new User();
        user.setId(id);
        user.setEmployeeCode("E%03d".formatted(id));
        user.setName("テストユーザー" + id);
        user.setEmail("user%s@example.com".formatted(id));
        user.setPassword("password");
        user.setRole(role);
        user.setDepartment("開発部");
        user.setEnabled(true);
        return user;
    }
}
