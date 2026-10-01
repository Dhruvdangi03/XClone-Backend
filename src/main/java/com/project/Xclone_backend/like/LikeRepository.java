package com.project.Xclone_backend.like;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LikeRepository extends JpaRepository<PostLike, Long> {

    /** Idempotent: returns 1 if a new like was created, 0 if it already existed. */
    @Modifying
    @Query(value = """
            insert into likes (user_id, post_id, created_at)
            values (:userId, :postId, now())
            on conflict (user_id, post_id) do nothing
            """, nativeQuery = true)
    int like(Long userId, Long postId);

    @Modifying
    @Query("delete from PostLike l where l.user.id = :userId and l.post.id = :postId")
    int unlike(Long userId, Long postId);

    /** Call {@code PostRepository.decrementLikeCountsForLiker} first so like counts stay correct. */
    @Modifying
    @Query("delete from PostLike l where l.user.id = :userId")
    void deleteAllByUser(Long userId);

    @Query("select l.post.id from PostLike l where l.user.id = :userId and l.post.id in :postIds")
    Set<Long> findLikedPostIds(Long userId, Collection<Long> postIds);

    @Query("""
            select l from PostLike l join fetch l.user
            where l.post.id = :postId and l.id < :cursor order by l.id desc
            """)
    List<PostLike> findLikers(Long postId, long cursor, Limit limit);

    /** Posts a user liked, most recently liked first. */
    @Query("""
            select l from PostLike l join fetch l.post p join fetch p.author
            where l.user.id = :userId and p.deleted = false and l.id < :cursor order by l.id desc
            """)
    List<PostLike> findUserLikes(Long userId, long cursor, Limit limit);
}
