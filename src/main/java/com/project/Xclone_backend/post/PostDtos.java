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
            Long replyToId,
            Long quotedPostId) {
    }

    public record UpdatePostRequest(@NotBlank @Size(max = Post.MAX_LENGTH) String content) {
    }

    /**
     * For a repost, every field describes the original post and {@code repostedBy} is who reposted it. For a quote
     * post, {@code quotedPost} is the quoted post (one level only), or null if it has since been deleted.
     */
    public record PostResponse(Long id, UserSummary author, String content, List<String> mediaUrls,
            Long replyToId, int likeCount, int replyCount, boolean likedByMe, Instant createdAt,
            int repostCount, boolean repostedByMe, UserSummary repostedBy,
            PostResponse quotedPost, List<UserSummary> mentions) {
    }
}
