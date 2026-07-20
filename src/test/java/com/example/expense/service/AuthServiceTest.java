package com.example.expense.service;

import com.example.expense.common.enums.RoleType;
import com.example.expense.dto.request.LoginRequest;
import com.example.expense.entity.User;
import com.example.expense.repository.UserMapper;
import com.example.expense.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserMapper userMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, userMapper);
    }

    @Test
    void authenticate_正常系_failure状態をresetする() {
        User user = user();
        SecurityUser principal = new SecurityUser(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        Authentication result = authService.authenticate(loginRequest());

        assertThat(result).isSameAs(authentication);
        verify(userMapper).recordLoginSuccess(1L);
        assertThat(authService.createAuthResponse(result).getAuthenticationType()).isEqualTo("Session");
    }

    @Test
    void authenticate_異常系_password不一致を記録する() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));
        when(userMapper.findByEmail("user@example.com")).thenReturn(user());

        assertThatThrownBy(() -> authService.authenticate(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);

        verify(userMapper).recordLoginFailure(
                eq(1L),
                eq(AuthService.MAX_FAILED_ATTEMPTS),
                any(LocalDateTime.class)
        );
        verify(userMapper, never()).recordLoginSuccess(any());
    }

    @Test
    void authenticate_異常系_lock中はfailure回数を増やさない() {
        User user = user();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));
        when(userMapper.findByEmail("user@example.com")).thenReturn(user);

        assertThatThrownBy(() -> authService.authenticate(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);

        verify(userMapper, never()).recordLoginFailure(
                any(),
                anyInt(),
                any(LocalDateTime.class)
        );
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password123!");
        return request;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmployeeCode("E0001");
        user.setName("一般ユーザー");
        user.setEmail("user@example.com");
        user.setPassword("encoded-password");
        user.setRole(RoleType.USER);
        user.setDepartment("営業部");
        user.setEnabled(true);
        user.setFailedLoginAttempts(0);
        return user;
    }
}
