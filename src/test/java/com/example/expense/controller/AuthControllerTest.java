package com.example.expense.controller;

import com.example.expense.common.GlobalExceptionHandler;
import com.example.expense.common.enums.RoleType;
import com.example.expense.config.SecurityConfig;
import com.example.expense.entity.User;
import com.example.expense.dto.response.AuthResponse;
import com.example.expense.dto.response.UserResponse;
import com.example.expense.security.RestAccessDeniedHandler;
import com.example.expense.security.RestAuthenticationEntryPoint;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void login_正常系_認証情報とユーザーを返す() throws Exception {
        UserResponse user = new UserResponse();
        user.setId(1L);
        user.setEmployeeCode("E001");
        user.setName("テストユーザー");
        user.setEmail("user@example.com");
        user.setRole(RoleType.USER);
        user.setRoleName(RoleType.USER.getDisplayName());

        Authentication authentication = authentication();
        when(authService.authenticate(argThat(request ->
                request.getEmail().equals("user@example.com")
                        && request.getPassword().equals("Password123!")
        ))).thenReturn(authentication);
        when(authService.createAuthResponse(authentication))
                .thenReturn(new AuthResponse("Session", user));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ログインに成功しました。"))
                .andExpect(jsonPath("$.data.authenticationType").value("Session"))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.role").value("USER"));

        verify(authService).authenticate(argThat(request -> request.getEmail().equals("user@example.com")));
    }

    @Test
    void csrf_正常系_token情報を返す() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.data.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    private Authentication authentication() {
        User entity = new User();
        entity.setId(1L);
        entity.setEmployeeCode("E001");
        entity.setName("テストユーザー");
        entity.setEmail("user@example.com");
        entity.setPassword("encoded-password");
        entity.setRole(RoleType.USER);
        entity.setDepartment("開発部");
        entity.setEnabled(true);
        SecurityUser principal = new SecurityUser(entity);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
    }
}
