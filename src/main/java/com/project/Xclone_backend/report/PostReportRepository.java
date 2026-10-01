package com.project.Xclone_backend.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    /** Returns 1 if the report was created, 0 if this reporter already reported this post. */
    @Modifying
    @Query(value = """
            insert into post_reports (reporter_id, post_id, reason, created_at)
            values (:reporterId, :postId, :reason, now())
            on conflict (reporter_id, post_id) do nothing
            """, nativeQuery = true)
    int report(Long reporterId, Long postId, String reason);

    boolean existsByReporterIdAndPostId(Long reporterId, Long postId);
}
