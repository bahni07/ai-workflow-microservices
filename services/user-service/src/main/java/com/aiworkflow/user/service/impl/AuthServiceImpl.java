package com.aiworkflow.user.service.impl;

import com.aiworkflow.user.dto.AuthResponse;
import com.aiworkflow.user.dto.LoginRequest;
import com.aiworkflow.user.dto.RegisterRequest;
import com.aiworkflow.user.entity.User;
import com.aiworkflow.user.exception.InvalidCredentialsException;
import com.aiworkflow.user.exception.UserAlreadyExistsException;
import com.aiworkflow.user.repository.UserRepository;
import com.aiworkflow.user.security.JwtService;
import com.aiworkflow.user.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("username", request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("email", request.email());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        log.info("User registered: userId={} username={}", saved.getId(), saved.getUsername());

        String token = jwtService.generateToken(saved.getUsername());
        return new AuthResponse(saved.getId(), saved.getUsername(), token);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        log.info("User logged in: userId={} username={}", user.getId(), user.getUsername());

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(user.getId(), user.getUsername(), token);
    }
}
