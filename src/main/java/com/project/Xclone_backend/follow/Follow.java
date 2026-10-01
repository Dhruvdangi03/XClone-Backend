package com.project.Xclone_backend.follow;

import java.time.Instant;

import com.project.Xclone_backend.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inserted via a native ON CONFLICT query in {@link FollowRepository}; the surrogate id gives follower lists a stable
 * "most recent first" cursor.
 */
@Entity
@Table(name = "follows",
        uniqueConstraints = @UniqueConstraint(name = "uk_follows_pair", columnNames = {"follower_id", "followee_id"}),
        indexes = @Index(name = "idx_follows_followee", columnList = "followee_id"))
@Getter
@NoArgsConstructor
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    @Column(nullable = false)
    private Instant createdAt;
}
