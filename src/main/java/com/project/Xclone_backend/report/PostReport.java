package com.project.Xclone_backend.report;

import java.time.Instant;

import com.project.Xclone_backend.post.Post;
import com.project.Xclone_backend.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Inserted via a native ON CONFLICT query in {@link PostReportRepository}; one report per reporter and post. */
@Entity
@Table(name = "post_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_reports_pair", columnNames = {"reporter_id", "post_id"}))
@Getter
@NoArgsConstructor
public class PostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    @Column(nullable = false)
    private Instant createdAt;
}
