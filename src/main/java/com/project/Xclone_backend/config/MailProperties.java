package com.project.Xclone_backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the emails the app sends. SMTP itself is configured through the standard {@code spring.mail.*}. */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from, String frontendUrl, Duration verifyTtl, Duration resetTtl) {
}
