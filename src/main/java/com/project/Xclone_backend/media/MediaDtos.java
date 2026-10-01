package com.project.Xclone_backend.media;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class MediaDtos {

    private MediaDtos() {
    }

    public record UploadUrlRequest(@NotBlank String contentType, @NotNull @Positive Long contentLength) {
    }

    /**
     * The client must PUT the file to {@code uploadUrl} sending exactly {@code headers}, then pass {@code key} as a
     * media key when creating a post or updating the profile.
     */
    public record UploadUrlResponse(String key, String uploadUrl, Map<String, List<String>> headers,
            String publicUrl, Instant expiresAt) {
    }
}
