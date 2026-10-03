package com.project.Xclone_backend.post;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Sets {@code root_id} on replies created before conversations were tracked, by following parent links up to the
 * top-level post. Idempotent: only rows without a root are touched, so it is a no-op once everything is filled.
 */
@Component
@RequiredArgsConstructor
public class PostRootBackfillInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostRootBackfillInitializer.class);

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = jdbc.update("""
                    with recursive chain(id, root_id) as (
                        select id, parent_id from posts where parent_id is not null
                        union all
                        select c.id, p.parent_id from chain c join posts p on p.id = c.root_id
                        where p.parent_id is not null
                    ),
                    tops as (
                        select c.id, c.root_id from chain c join posts p on p.id = c.root_id
                        where p.parent_id is null
                    )
                    update posts set root_id = tops.root_id
                    from tops
                    where posts.id = tops.id and posts.root_id is null
                    """);
            if (updated > 0) {
                log.info("Backfilled conversation root for {} existing replies", updated);
            }
        } catch (RuntimeException e) {
            log.warn("Could not backfill reply roots; reply controls will not apply to older replies: {}",
                    e.getMessage());
        }
    }
}
