package com.project.Xclone_backend.follow;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    /** Idempotent: returns 1 if a new follow was created, 0 if it already existed. */
    @Modifying
    @Query(value = """
            insert into follows (follower_id, followee_id, created_at)
            values (:followerId, :followeeId, now())
            on conflict (follower_id, followee_id) do nothing
            """, nativeQuery = true)
    int follow(Long followerId, Long followeeId);

    @Modifying
    @Query("delete from Follow f where f.follower.id = :followerId and f.followee.id = :followeeId")
    int unfollow(Long followerId, Long followeeId);

    @Modifying
    @Query("delete from Follow f where f.follower.id = :userId or f.followee.id = :userId")
    void deleteAllInvolving(Long userId);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    long countByFolloweeId(Long followeeId);

    long countByFollowerId(Long followerId);

    @Query("""
            select f from Follow f join fetch f.follower
            where f.followee.id = :userId and f.id < :cursor order by f.id desc
            """)
    List<Follow> findFollowers(Long userId, long cursor, Limit limit);

    @Query("""
            select f from Follow f join fetch f.followee
            where f.follower.id = :userId and f.id < :cursor order by f.id desc
            """)
    List<Follow> findFollowing(Long userId, long cursor, Limit limit);

    @Query("select f.followee.id from Follow f where f.follower.id = :followerId and f.followee.id in :ids")
    Set<Long> findFolloweeIdsAmong(Long followerId, Collection<Long> ids);
}
