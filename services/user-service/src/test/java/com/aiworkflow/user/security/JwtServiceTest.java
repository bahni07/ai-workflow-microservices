package com.aiworkflow.user.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        // 64-char hex string = 32 bytes = 256-bit key (meets HS256 minimum)
        props.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        props.setExpirationMs(86400000L);
        jwtService = new JwtService(props);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken("alice");
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsCorrectSubject() {
        String token = jwtService.generateToken("alice");
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateToken("alice");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.valid.token")).isFalse();
    }
}
