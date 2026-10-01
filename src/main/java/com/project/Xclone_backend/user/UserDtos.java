package com.project.Xclone_backend.user;

import java.time.Instant;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    /** Full user, returned for the current user and on auth. */
    public record UserResponse(Long id, String username, String email, String displayName, String bio,
            String avatarUrl, String bannerUrl, Instant createdAt) {
    }

    /** Compact user embedded in posts and user lists. */
    public record UserSummary(Long id, String username, String displayName, String avatarUrl) {
    }

    public record ProfileResponse(Long id, String username, String displayName, String bio, String avatarUrl,
            String bannerUrl, Instant createdAt, long followerCount, long followingCount, boolean followedByMe) {
    }

    /** Null fields are left unchanged. An empty string clears bio/avatar/banner. */
    public record UpdateProfileRequest(
            @Size(min = 1, max = 50) String displayName,
            @Size(max = 160) String bio,
            @Pattern(regexp = "^$|^users/.+", message = "must be an uploaded media key") String avatarKey,
            @Pattern(regexp = "^$|^users/.+", message = "must be an uploaded media key") String bannerKey) {
    }
}
