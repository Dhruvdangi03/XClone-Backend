package com.project.Xclone_backend.post;

import java.time.Instant;
import java.util.List;

import com.project.Xclone_backend.user.UserDtos.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PostDtos {

    private PostDtos() {
    }

    /** Needs non-blank content, at least one media key, or both. */
    public record CreatePostRequest(
            @Size(max = Post.MAX_LENGTH) String content,
            @Size(max = Post.MAX_MEDIA) List<@NotBlank @Pattern(regexp = "^users/.+",
                    message = "must be an uploaded media key") String> mediaKeys,
            Long replyToId) {
    }

    public record UpdatePostRequest(@NotBlank @Size(max = Post.MAX_LENGTH) String content) {
    }

    public record PostResponse(Long id, UserSummary author, String content, List<String> mediaUrls,
            Long replyToId, int likeCount, int replyCount, boolean likedByMe, Instant createdAt) {
    }
}
