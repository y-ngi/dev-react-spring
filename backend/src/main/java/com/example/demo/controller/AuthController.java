package com.example.demo.controller;

import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            @Value("${app.cookie.secure:true}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrfToken(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken());
    }

    @PostMapping("/login-request")
    public ResponseEntity<Void> requestLogin(@Valid @RequestBody LoginRequest request) {
        authService.requestLogin(request.email(), request.password());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login-verify")
    public ResponseEntity<Void> verifyLogin(@Valid @RequestBody VerifyRequest request) {
        String token = authService.verifyLogin(request.email(), request.code());
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(15))
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record VerifyRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}

    public record CsrfResponse(String token) {}
}
