package com.example.demo.service;

import com.example.demo.repository.LoginChallenge;
import com.example.demo.repository.LoginChallengeRepository;
import com.example.demo.repository.UserAuthDto;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final LoginChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final JwtService jwtService;
    private final String mailFrom;

    public AuthService(
            UserRepository userRepository,
            LoginChallengeRepository challengeRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            JwtService jwtService,
            @Value("${app.mail.from}") String mailFrom) {
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.jwtService = jwtService;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public void requestLogin(String email, String password) {
        UserAuthDto user = userRepository.findAuthUserByEmail(email).orElse(null);
        if (user == null || user.passwordHash() == null
                || !passwordEncoder.matches(password, user.passwordHash())
                || !"ACTIVE".equals(user.status())) {
            return;
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        challengeRepository.create(user.id(), hash(code), LocalDateTime.now().plusMinutes(5));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.email());
        message.setSubject("Your sign-in verification code");
        message.setText("Your verification code is %s. It expires in 5 minutes.".formatted(code));
        mailSender.send(message);
    }

    @Transactional
    public String verifyLogin(String email, String code) {
        LoginChallenge challenge = challengeRepository.findCurrentByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid verification code"));
        if (!MessageDigest.isEqual(
                challenge.codeHash().getBytes(StandardCharsets.US_ASCII),
                hash(code).getBytes(StandardCharsets.US_ASCII))) {
            throw new BadCredentialsException("Invalid verification code");
        }

        UserAuthDto user = userRepository.findAuthUserById(challenge.userId())
                .filter(candidate -> "ACTIVE".equals(candidate.status()))
                .orElseThrow(() -> new BadCredentialsException("Invalid verification code"));
        if (!challengeRepository.consume(challenge.id())) {
            throw new BadCredentialsException("Invalid verification code");
        }
        return jwtService.issue(user.id());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
