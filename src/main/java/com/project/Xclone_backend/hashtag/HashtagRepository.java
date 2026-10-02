package com.project.Xclone_backend.hashtag;

import java.util.Collection;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.project.Xclone_backend.hashtag.HashtagDtos.TrendingHashtag;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    /** Idempotent and safe under concurrent posts introducing the same tag. */
    @Modifying
    @Query(value = "insert into hashtags (name) values (:name) on conflict (name) do nothing", nativeQuery = true)
    void insertIfAbsent(String name);

    List<Hashtag> findByNameIn(Collection<String> names);

    /**
     * Tags used by live posts created since {@code since}, ranked by distinct authors (so one account spamming a
     * tag cannot trend it alone), then by post count, then name for a stable order.
     */
    @Query("""
            select new com.project.Xclone_backend.hashtag.HashtagDtos$TrendingHashtag(
                   h.name, count(distinct p.id), count(distinct p.author.id))
            from Post p join p.hashtags h
            where p.deleted = false and p.createdAt >= :since
            group by h.name
            order by count(distinct p.author.id) desc, count(distinct p.id) desc, h.name asc
            """)
    List<TrendingHashtag> findTrending(Instant since, Limit limit);
}
