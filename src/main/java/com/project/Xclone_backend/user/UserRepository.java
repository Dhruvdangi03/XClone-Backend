package com.project.Xclone_backend.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByIdAndStatus(Long id, AccountStatus status);

    /** {@code prefix} must already be lowercased, LIKE-escaped and end with {@code %}. */
    @Query("""
            select u from User u
            where (u.username like :prefix escape '\\' or lower(u.displayName) like :prefix escape '\\')
              and u.status <> com.project.Xclone_backend.user.AccountStatus.DELETED
            order by u.username
            """)
    List<User> search(String prefix, Limit limit);
}
