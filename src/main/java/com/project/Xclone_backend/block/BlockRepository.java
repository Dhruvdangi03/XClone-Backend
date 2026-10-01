package com.project.Xclone_backend.block;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<Block, Long> {

    /** Idempotent: returns 1 if a new block was created, 0 if it already existed. */
    @Modifying
    @Query(value = """
            insert into blocks (blocker_id, blocked_id, created_at)
            values (:blockerId, :blockedId, now())
            on conflict (blocker_id, blocked_id) do nothing
            """, nativeQuery = true)
    int block(Long blockerId, Long blockedId);

    @Modifying
    @Query("delete from Block b where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    int unblock(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /** True if either user has blocked the other. */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blocker.id = :userId and b.blocked.id = :otherId)
               or (b.blocker.id = :otherId and b.blocked.id = :userId)
            """)
    boolean existsBetween(Long userId, Long otherId);

    @Query("""
            select b from Block b join fetch b.blocked
            where b.blocker.id = :userId and b.id < :cursor order by b.id desc
            """)
    List<Block> findBlocked(Long userId, long cursor, Limit limit);
}
