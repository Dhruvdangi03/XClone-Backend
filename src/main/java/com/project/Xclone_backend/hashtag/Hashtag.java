package com.project.Xclone_backend.hashtag;

import com.project.Xclone_backend.post.Post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row per distinct tag. Inserted via a native ON CONFLICT query in {@link HashtagRepository}. */
@Entity
@Table(name = "hashtags")
@Getter
@NoArgsConstructor
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lowercase, without the leading '#'. */
    @Column(nullable = false, unique = true, length = Post.MAX_LENGTH)
    private String name;
}
