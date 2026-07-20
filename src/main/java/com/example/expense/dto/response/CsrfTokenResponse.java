package com.example.expense.dto.response;

import org.springframework.security.web.csrf.CsrfToken;

public class CsrfTokenResponse {

    private final String headerName;
    private final String parameterName;
    private final String token;

    public CsrfTokenResponse(CsrfToken csrfToken) {
        this.headerName = csrfToken.getHeaderName();
        this.parameterName = csrfToken.getParameterName();
        this.token = csrfToken.getToken();
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getToken() {
        return token;
    }
}
