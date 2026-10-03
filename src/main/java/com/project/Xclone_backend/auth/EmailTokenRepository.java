package com.project.Xclone_backend.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<EmailToken> findByTokenHashAndType(String tokenHash, EmailTokenType type);

    /** Returns 1 only for the caller that actually removed the row, so a token can be used at most once. */
    @Modifying
    @Query("delete from EmailToken t where t.id = :id")
    int deleteByTokenId(Long id);

    @Modifying
    @Query("delete from EmailToken t where t.user.id = :userId and t.type = :type")
    void deleteAllForUserAndType(Long userId, EmailTokenType type);

    @Modifying
    @Query("delete from EmailToken t where t.user.id = :userId")
    void deleteAllForUser(Long userId);
}
