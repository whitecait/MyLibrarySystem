package com.library.dto;

import com.library.entity.enums.UserRole;

public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private UserRole role;
    private Long readerId;

    public LoginResponse() {}

    public LoginResponse(String token, Long userId, String username, UserRole role, Long readerId) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.readerId = readerId;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Long getReaderId() { return readerId; }
    public void setReaderId(Long readerId) { this.readerId = readerId; }
}
