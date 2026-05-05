package com.aiworkflow.user.service;

import com.aiworkflow.user.dto.AuthResponse;
import com.aiworkflow.user.dto.LoginRequest;
import com.aiworkflow.user.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
