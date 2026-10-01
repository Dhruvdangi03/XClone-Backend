package com.project.Xclone_backend.block;

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
 * Inserted via a native ON CONFLICT query in {@link BlockRepository}. A block hides content in both directions, so
 * lookups check the pair either way round.
 */
@Entity
@Table(name = "blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_blocks_pair", columnNames = {"blocker_id", "blocked_id"}),
        indexes = @Index(name = "idx_blocks_blocked", columnList = "blocked_id"))
@Getter
@NoArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(nullable = false)
    private Instant createdAt;
}
