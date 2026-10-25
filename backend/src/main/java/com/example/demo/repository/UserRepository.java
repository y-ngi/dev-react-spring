package com.example.demo.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UserAuthDto> findAuthUserByEmail(String email) {
        return jdbcClient.sql("""
                        SELECT id, email, password_hash AS passwordHash, status, role,
                               two_factor_enabled AS twoFactorEnabled
                        FROM users
                        WHERE email = :email
                        """)
                .param("email", email)
                .query(UserAuthDto.class)
                .optional();
    }

    public Optional<UserAuthDto> findAuthUserById(Long userId) {
        return jdbcClient.sql("""
                        SELECT id, email, password_hash AS passwordHash, status, role,
                               two_factor_enabled AS twoFactorEnabled
                        FROM users
                        WHERE id = :id
                        """)
                .param("id", userId)
                .query(UserAuthDto.class)
                .optional();
    }
}
