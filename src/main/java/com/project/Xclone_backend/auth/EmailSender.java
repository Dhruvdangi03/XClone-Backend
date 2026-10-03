package com.project.Xclone_backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.project.Xclone_backend.config.MailProperties;

import lombok.RequiredArgsConstructor;

/**
 * Sends plain-text email through SMTP when {@code spring.mail.host} is configured; otherwise logs the message, so the
 * app still works locally and the emailed links can be copied from the log. Failures are logged, never thrown.
 */
@Component
@RequiredArgsConstructor
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties props;

    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Email not sent (no SMTP configured). To: {} | {} | {}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(props.from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}
