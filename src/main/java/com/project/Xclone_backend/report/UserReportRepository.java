package com.project.Xclone_backend.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    /** Returns 1 if the report was created, 0 if this reporter already reported this user. */
    @Modifying
    @Query(value = """
            insert into user_reports (reporter_id, reported_user_id, reason, created_at)
            values (:reporterId, :reportedUserId, :reason, now())
            on conflict (reporter_id, reported_user_id) do nothing
            """, nativeQuery = true)
    int report(Long reporterId, Long reportedUserId, String reason);

    boolean existsByReporterIdAndReportedUserId(Long reporterId, Long reportedUserId);
}
