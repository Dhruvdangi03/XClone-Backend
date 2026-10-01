package com.project.Xclone_backend.post;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("select p from Post p join fetch p.author where p.id = :id and p.deleted = false")
    Optional<Post> findLive(Long id);

    /** Top-level posts by one author, newest first. */
    @Query("""
            select p from Post p join fetch p.author
            where p.author.id = :authorId and p.parent is null and p.deleted = false and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findUserPosts(Long authorId, long cursor, Limit limit);

    /** Replies by one author, newest first. */
    @Query("""
            select p from Post p join fetch p.author
            where p.author.id = :authorId and p.parent is not null and p.deleted = false and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findUserReplies(Long authorId, long cursor, Limit limit);

    /** Direct replies to a post, oldest first so a thread reads top to bottom. */
    @Query("""
            select p from Post p join fetch p.author
            where p.parent.id = :parentId and p.deleted = false and p.id > :cursor
            order by p.id asc
            """)
    List<Post> findReplies(Long parentId, long cursor, Limit limit);

    /** Posts and replies tagged with a normalized hashtag name, newest first. */
    @Query("""
            select p from Post p join fetch p.author join p.hashtags h
            where h.name = :name and p.deleted = false and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findByHashtag(String name, long cursor, Limit limit);

    /** Home timeline: top-level posts by the user and everyone they follow, newest first. */
    @Query("""
            select p from Post p join fetch p.author
            where (p.author.id = :userId
                   or p.author.id in (select f.followee.id from Follow f where f.follower.id = :userId))
              and p.parent is null and p.deleted = false and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findTimeline(Long userId, long cursor, Limit limit);

    @Modifying
    @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :id")
    void addToLikeCount(Long id, int delta);

    @Modifying
    @Query("update Post p set p.replyCount = p.replyCount + :delta where p.id = :id")
    void addToReplyCount(Long id, int delta);
}
