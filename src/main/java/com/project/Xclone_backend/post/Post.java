package com.project.Xclone_backend.post;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import com.project.Xclone_backend.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_author_id", columnList = "author_id, id"),
        @Index(name = "idx_posts_parent_id", columnList = "parent_id, id")})
@Getter
@Setter
@NoArgsConstructor
public class Post {

    public static final int MAX_LENGTH = 280;
    public static final int MAX_MEDIA = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    /** Non-null when this post is a reply. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Post parent;

    @Column(nullable = false)
    private int likeCount;

    @Column(nullable = false)
    private int replyCount;

    /** Soft delete keeps reply threads intact. */
    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    @BatchSize(size = 50)
    private List<PostMedia> media = new ArrayList<>();

    public void addMedia(String r2Key) {
        PostMedia m = new PostMedia();
        m.setPost(this);
        m.setR2Key(r2Key);
        m.setPosition(media.size());
        media.add(m);
    }
}
