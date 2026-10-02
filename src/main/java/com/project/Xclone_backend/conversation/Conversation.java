package com.project.Xclone_backend.conversation;

import java.time.Instant;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.project.Xclone_backend.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A one-to-one conversation. The pair is stored in canonical order ({@code userOne.id < userTwo.id}), so the unique
 * constraint prevents duplicates whichever user starts the conversation and the check constraint rules out
 * self-conversations. Inserted via a native ON CONFLICT query in {@link ConversationRepository}.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversations_pair",
                columnNames = {"user_one_id", "user_two_id"}),
        indexes = @Index(name = "idx_conversations_user_two", columnList = "user_two_id"))
@Check(name = "ck_conversations_order", constraints = "user_one_id < user_two_id")
@Getter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_one_id", nullable = false)
    private User userOne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_two_id", nullable = false)
    private User userTwo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()") // lets ddl-auto=update add the column to a table that already has rows
    @Column(nullable = false)
    private Instant updatedAt;
}
