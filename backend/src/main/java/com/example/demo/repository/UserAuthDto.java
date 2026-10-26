package com.example.demo.repository;

public record UserAuthDto(Long id, String email, String passwordHash, String status, String role,
                          boolean twoFactorEnabled) {}
