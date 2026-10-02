package com.project.Xclone_backend.bookmark;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /** Returns 1 if a new bookmark was created, 0 if it already existed. */
    @Modifying
    @Query(value = """
            insert into bookmarks (user_id, post_id, created_at)
            values (:userId, :postId, now())
            on conflict (user_id, post_id) do nothing
            """, nativeQuery = true)
    int bookmark(Long userId, Long postId);

    @Modifying
    @Query("delete from Bookmark b where b.user.id = :userId and b.post.id = :postId")
    int unbookmark(Long userId, Long postId);

    @Modifying
    @Query("delete from Bookmark b where b.user.id = :userId")
    void deleteAllByUser(Long userId);

    /** Posts a user bookmarked, most recently bookmarked first. Deleted posts are skipped. */
    @Query("""
            select b from Bookmark b join fetch b.post p join fetch p.author
            where b.user.id = :userId and p.deleted = false and b.id < :cursor order by b.id desc
            """)
    List<Bookmark> findUserBookmarks(Long userId, long cursor, Limit limit);
}
