package com.project.Xclone_backend.hashtag;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    /** Idempotent and safe under concurrent posts introducing the same tag. */
    @Modifying
    @Query(value = "insert into hashtags (name) values (:name) on conflict (name) do nothing", nativeQuery = true)
    void insertIfAbsent(String name);

    List<Hashtag> findByNameIn(Collection<String> names);
}
