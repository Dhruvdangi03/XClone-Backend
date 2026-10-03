package com.project.Xclone_backend.websocket;

/** Sent to the caller only, on /user/queue/errors. */
public record SocketError(int status, String detail) {
}
