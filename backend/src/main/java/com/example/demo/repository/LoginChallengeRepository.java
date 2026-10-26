package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LoginChallengeRepository {
    private final JdbcClient jdbcClient;

    public LoginChallengeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void create(Long userId, String codeHash, LocalDateTime expiresAt) {
        jdbcClient.sql("""
                        INSERT INTO login_challenges (user_id, code_hash, expires_at)
                        VALUES (:userId, :codeHash, :expiresAt)
                        """)
                .param("userId", userId)
                .param("codeHash", codeHash)
                .param("expiresAt", expiresAt)
                .update();
    }

    public Optional<LoginChallenge> findCurrentByEmail(String email) {
        return jdbcClient.sql("""
                        SELECT lc.id, lc.user_id AS userId, lc.code_hash AS codeHash
                        FROM login_challenges lc
                        JOIN users u ON u.id = lc.user_id
                        WHERE u.email = :email
                          AND lc.used = FALSE
                          AND lc.expires_at > CURRENT_TIMESTAMP
                        ORDER BY lc.created_at DESC
                        LIMIT 1
                        """)
                .param("email", email)
                .query(LoginChallenge.class)
                .optional();
    }

    public boolean consume(Long id) {
        return jdbcClient.sql("""
                        UPDATE login_challenges
                        SET used = TRUE
                        WHERE id = :id AND used = FALSE AND expires_at > CURRENT_TIMESTAMP
                        """)
                .param("id", id)
                .update() == 1;
    }
}
