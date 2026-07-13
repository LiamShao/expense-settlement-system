package com.example.expense.controller;

import com.example.expense.common.GlobalExceptionHandler;
import com.example.expense.common.enums.RoleType;
import com.example.expense.config.SecurityConfig;
import com.example.expense.dto.response.AuthResponse;
import com.example.expense.dto.response.UserResponse;
import com.example.expense.security.RestAccessDeniedHandler;
import com.example.expense.security.RestAuthenticationEntryPoint;
import com.example.expense.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

        when(authService.login(argThat(request ->
                request.getEmail().equals("user@example.com")
                        && request.getPassword().equals("Password123!")
        ))).thenReturn(new AuthResponse("Basic", user));

        mockMvc.perform(post("/api/auth/login")
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
                .andExpect(jsonPath("$.data.authenticationType").value("Basic"))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.role").value("USER"));

        verify(authService).login(argThat(request -> request.getEmail().equals("user@example.com")));
    }
}
