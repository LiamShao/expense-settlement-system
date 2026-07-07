package com.example.expense.dto.response;

public class AuthResponse {

    private String authenticationType;
    private UserResponse user;

    public AuthResponse() {
    }

    public AuthResponse(String authenticationType, UserResponse user) {
        this.authenticationType = authenticationType;
        this.user = user;
    }

    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }
}
