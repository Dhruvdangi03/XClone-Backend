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

    /** Direct replies to a post, oldest first so a thread reads top to bottom. Skips viewer-blocked authors. */
    @Query("""
            select p from Post p join fetch p.author
            where p.parent.id = :parentId and p.deleted = false and p.id > :cursor
              and (:viewerId is null or not exists (select 1 from Block b
                   where (b.blocker.id = :viewerId and b.blocked.id = p.author.id)
                      or (b.blocker.id = p.author.id and b.blocked.id = :viewerId)))
            order by p.id asc
            """)
    List<Post> findReplies(Long parentId, Long viewerId, long cursor, Limit limit);

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

    // --- Account deletion. Count fixes must run before the likes are deleted and the posts are soft-deleted.

    /** Takes the author's live replies out of their parents' reply counts. */
    @Modifying
    @Query(value = """
            update posts p set reply_count = p.reply_count - r.n
            from (select parent_id, count(*) as n from posts
                  where author_id = :authorId and deleted = false and parent_id is not null
                  group by parent_id) r
            where p.id = r.parent_id
            """, nativeQuery = true)
    void decrementReplyCountsForAuthor(Long authorId);

    /** Takes the user's likes out of the like counts of the posts they liked. */
    @Modifying
    @Query(value = """
            update posts p set like_count = p.like_count - 1
            from likes l where l.post_id = p.id and l.user_id = :userId
            """, nativeQuery = true)
    void decrementLikeCountsForLiker(Long userId);

    @Modifying
    @Query(value = "delete from post_hashtags where post_id in (select id from posts where author_id = :authorId)",
            nativeQuery = true)
    void deleteHashtagLinksForAuthor(Long authorId);

    @Modifying
    @Query("delete from PostMedia m where m.post.id in (select p.id from Post p where p.author.id = :authorId)")
    void deleteMediaForAuthor(Long authorId);

    /** Soft delete keeps other users' replies attached; the text is cleared because it is the author's data. */
    @Modifying
    @Query("update Post p set p.deleted = true, p.content = '' where p.author.id = :authorId")
    void softDeleteAndClearAllByAuthor(Long authorId);
}
