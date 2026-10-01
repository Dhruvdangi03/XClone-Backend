package com.project.Xclone_backend.like;

import java.time.Instant;

import com.project.Xclone_backend.post.Post;
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
 * Named PostLike because LIKE is a JPQL keyword. Inserted via a native ON CONFLICT query in {@link LikeRepository}.
 */
@Entity
@Table(name = "likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_likes_pair", columnNames = {"user_id", "post_id"}),
        indexes = @Index(name = "idx_likes_post", columnList = "post_id"))
@Getter
@NoArgsConstructor
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false)
    private Instant createdAt;
}
