package com.project.Xclone_backend.post;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Trigram index so substring post search does not scan the whole table. Hibernate's {@code @Index} cannot express
 * it, so it is created here. Best effort: search still works without it, only slower.
 */
@Component
@RequiredArgsConstructor
public class PostSearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostSearchIndexInitializer.class);

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("create extension if not exists pg_trgm");
            jdbc.execute("create index if not exists idx_posts_content_trgm on posts using gin (lower(content) gin_trgm_ops)");
        } catch (RuntimeException e) {
            log.warn("Could not create post search index; search will fall back to sequential scans: {}", e.getMessage());
        }
    }
}
