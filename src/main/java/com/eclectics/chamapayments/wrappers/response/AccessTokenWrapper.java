package com.eclectics.chamapayments.wrappers.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenWrapper {
    private int status;
    private String message;
    @JsonIgnore
    private String username;
    private String access_token;
    private String token_type;
    private String refresh_token;
    private Long expires_in;
    private String scope;
    private String jti;
    private boolean isFirstTimeLogin;
    private String language;
    public AccessTokenWrapper(int status, String message) {
        this.status = status;
        this.message = message;
    }
    public AccessTokenWrapper(int status, String message, String username) {
        this.status = status;
        this.message = message;
        this.username = username;
    }
}
