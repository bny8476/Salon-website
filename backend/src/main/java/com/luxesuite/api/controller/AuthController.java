package com.luxesuite.api.controller;

import com.luxesuite.api.dto.AuthResponse;
import com.luxesuite.api.dto.LoginRequest;
import com.luxesuite.api.dto.ForgotPasswordRequest;
import com.luxesuite.api.dto.ResetPasswordRequest;
import com.luxesuite.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import com.luxesuite.api.service.RedisService;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
// Security Note: @PreAuthorize is not required here as these are intentionally public endpoints for authentication, registration, and password recovery.
public class AuthController {

    private final AuthService authService;
    private final RedisService redisService;
    
    @org.springframework.beans.factory.annotation.Value("${app.cookie-secure:true}")
    private boolean secureCookie;

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String prefix = parts[0];
        if (prefix.length() > 1) {
            prefix = prefix.charAt(0) + "***";
        }
        return prefix + "@" + parts[1];
    }

    private void setCookies(HttpServletResponse response, AuthResponse authResponse) {
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", authResponse.getAccessToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("None")
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", authResponse.getRefreshToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("None")
                .path("/api/v1/auth/refresh")
                .maxAge(30 * 24 * 60 * 60) // 30 days
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearCookies(HttpServletResponse response) {
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("None")  // Must match the SameSite used at set time
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("None")  // Must match the SameSite used at set time
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        if (redisService.isRateLimited("login:" + clientIp, 10, 60000)) {
            log.warn("Rate limit exceeded for login from IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many login attempts. Please try again later.");
        }
        
        log.info("Login attempt for user: {}", maskEmail(request.getEmail()));
        AuthResponse authResponse = authService.authenticate(request);
        setCookies(response, authResponse);
        log.info("Login successful for user: {}", maskEmail(request.getEmail()));
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody com.luxesuite.api.dto.RegisterRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String clientIp = request.getRemoteAddr();
        if (redisService.isRateLimited("register:" + clientIp, 5, 3600000)) { // 5 requests per hour
            log.warn("Rate limit exceeded for registration from IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many registration attempts. Please try again later.");
        }
        
        log.info("Registration attempt for email: {}", maskEmail(requestDto.getEmail()));
        AuthResponse authResponse = authService.register(requestDto);
        setCookies(response, authResponse);
        log.info("Registration successful for email: {}", maskEmail(requestDto.getEmail()));
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String clientIp = request.getRemoteAddr();
        if (redisService.isRateLimited("refresh:" + clientIp, 20, 60000)) { // 20 requests per minute
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many refresh attempts.");
        }
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        
        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }
        
        AuthResponse authResponse = authService.refreshToken(refreshToken);
        setCookies(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String jwt = null;
        String refreshToken = null;
        
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                } else if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }
        
        authService.logout(jwt, refreshToken);
        clearCookies(response);
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        if (redisService.isRateLimited("forgotPassword:" + clientIp, 3, 3600000)) { // 3 per hour
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many password reset requests. Please try again later.");
        }
        authService.forgotPassword(request);
        return ResponseEntity.ok("If the email exists, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        if (redisService.isRateLimited("resetPassword:" + clientIp, 5, 3600000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many reset attempts.");
        }
        authService.resetPassword(request);
        return ResponseEntity.ok("Password has been reset successfully.");
    }
}
