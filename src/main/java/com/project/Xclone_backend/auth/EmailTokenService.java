package com.project.Xclone_backend.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.config.MailProperties;
import com.project.Xclone_backend.user.User;

import lombok.RequiredArgsConstructor;

/** Issues emailed single-use tokens (email verification, password reset) and redeems them. */
@Service
@RequiredArgsConstructor
public class EmailTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailTokenRepository repository;
    private final EmailSender emailSender;
    private final MailProperties props;

    @Transactional
    public void sendVerification(User user) {
        String token = issue(user, EmailTokenType.VERIFY_EMAIL, props.verifyTtl());
        emailSender.send(user.getEmail(), "Verify your email",
                "Confirm your email address: " + link("verify-email", token));
    }

    @Transactional
    public void sendPasswordReset(User user) {
        String token = issue(user, EmailTokenType.RESET_PASSWORD, props.resetTtl());
        emailSender.send(user.getEmail(), "Reset your password",
                "Reset your password: " + link("reset-password", token)
                        + "\nIf you did not ask for this, you can ignore this email.");
    }

    /** Returns the token's user and uses the token up. Unknown, wrong-type, expired and reused tokens are all rejected. */
    @Transactional
    public User consume(String rawToken, EmailTokenType type) {
        EmailToken token = repository.findByTokenHashAndType(AuthService.hash(rawToken), type)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired token"));
        if (repository.deleteByTokenId(token.getId()) == 0) {
            throw ApiException.badRequest("Invalid or expired token");
        }
        return token.getUser();
    }

    /** A user has at most one live token per type: issuing a new one invalidates the earlier one. */
    private String issue(User user, EmailTokenType type, java.time.Duration ttl) {
        repository.deleteAllForUserAndType(user.getId(), type);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailToken token = new EmailToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(AuthService.hash(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        repository.save(token);
        return raw;
    }

    private String link(String path, String token) {
        return props.frontendUrl() + "/" + path + "?token=" + token;
    }
}
