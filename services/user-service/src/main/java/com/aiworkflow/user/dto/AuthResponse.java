package com.aiworkflow.user.dto;

import java.util.UUID;

public record AuthResponse(UUID userId, String username, String token) {}
