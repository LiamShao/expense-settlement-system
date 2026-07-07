package com.example.expense.service;

import com.example.expense.dto.request.LoginRequest;
import com.example.expense.dto.response.AuthResponse;
import com.example.expense.dto.response.UserResponse;
import com.example.expense.entity.User;
import com.example.expense.repository.UserMapper;
import com.example.expense.security.SecurityUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;

    public AuthService(AuthenticationManager authenticationManager, UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return new AuthResponse("Basic", toUserResponse(securityUser.getUser()));
    }

    public UserResponse getCurrentUser(SecurityUser securityUser) {
        User user = userMapper.findById(securityUser.getId());
        return toUserResponse(user);
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
