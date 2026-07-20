package com.example.expense.service;

import com.example.expense.dto.request.LoginRequest;
import com.example.expense.dto.response.AuthResponse;
import com.example.expense.dto.response.UserResponse;
import com.example.expense.entity.User;
import com.example.expense.repository.UserMapper;
import com.example.expense.security.SecurityUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int LOCK_MINUTES = 15;

    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;

    public AuthService(AuthenticationManager authenticationManager, UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
    }

    public Authentication authenticate(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            userMapper.recordLoginSuccess(securityUser.getId());
            return authentication;
        } catch (BadCredentialsException exception) {
            recordLoginFailure(request.getEmail());
            throw exception;
        }
    }

    public AuthResponse createAuthResponse(Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return new AuthResponse("Session", toUserResponse(securityUser.getUser()));
    }

    public UserResponse getCurrentUser(SecurityUser securityUser) {
        User user = userMapper.findById(securityUser.getId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BadCredentialsException("Current account is unavailable");
        }
        return toUserResponse(user);
    }

    private void recordLoginFailure(String email) {
        User user = userMapper.findByEmail(email);
        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())
                || isCurrentlyLocked(user)) {
            return;
        }
        userMapper.recordLoginFailure(
                user.getId(),
                MAX_FAILED_ATTEMPTS,
                LocalDateTime.now().plusMinutes(LOCK_MINUTES)
        );
    }

    private boolean isCurrentlyLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmployeeCode(user.getEmployeeCode());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setRoleName(user.getRole().getDisplayName());
        response.setDepartment(user.getDepartment());
        return response;
    }
}
