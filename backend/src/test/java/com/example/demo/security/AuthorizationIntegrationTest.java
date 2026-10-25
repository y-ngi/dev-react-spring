package com.example.demo.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthorizationIntegrationTest {
    private static final String JWT_SECRET =
            "bG9jYWwtdGVzdC1qd3Qtc2VjcmV0LWF0LWxlYXN0LTMyLWJ5dGVz";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    JwtService jwtService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM login_challenges").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    void rejectsRequestsWithoutAValidToken() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")
                        .cookie(new Cookie("access_token", "invalid")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checksCurrentAccountStatusOnEveryRequest() throws Exception {
        long userId = createUser("active@example.com", "ACTIVE", "USER");
        String token = jwtService.issue(userId);
        jdbcClient.sql("UPDATE users SET status = 'LOCKED' WHERE id = :id")
                .param("id", userId)
                .update();

        mockMvc.perform(get("/api/users/me")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAdminRoleForAdminEndpoints() throws Exception {
        long userId = createUser("user@example.com", "ACTIVE", "USER");
        mockMvc.perform(get("/api/admin/health")
                        .cookie(new Cookie("access_token", jwtService.issue(userId))))
                .andExpect(status().isForbidden());

        long adminId = createUser("admin@example.com", "ACTIVE", "ADMIN");
        mockMvc.perform(get("/api/admin/health")
                        .cookie(new Cookie("access_token", jwtService.issue(adminId))))
                .andExpect(status().isOk());
    }

    private long createUser(String email, String status, String role) {
        jdbcClient.sql("""
                        INSERT INTO users (email, password_hash, status, role)
                        VALUES (:email, :passwordHash, :status, :role)
                        """)
                .param("email", email)
                .param("passwordHash", passwordEncoder.encode("test-password"))
                .param("status", status)
                .param("role", role)
                .update();
        return jdbcClient.sql("SELECT id FROM users WHERE email = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }
}
