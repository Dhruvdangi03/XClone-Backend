package com.project.Xclone_backend.websocket;

import java.security.Principal;

import com.project.Xclone_backend.security.AuthUser;

/** The authenticated STOMP session user. The name is the user id, which user destinations are routed by. */
public record StompPrincipal(AuthUser user) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(user.id());
    }
}
