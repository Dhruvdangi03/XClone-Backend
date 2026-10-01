package com.project.Xclone_backend.security;

/** Authenticated principal extracted from the access token. */
public record AuthUser(Long id, String username) {
}
