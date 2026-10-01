package com.project.Xclone_backend.media;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.Xclone_backend.media.MediaDtos.UploadUrlRequest;
import com.project.Xclone_backend.media.MediaDtos.UploadUrlResponse;
import com.project.Xclone_backend.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UploadUrlRequest req) {
        return mediaService.createUploadUrl(me.id(), req.contentType(), req.contentLength());
    }
}
