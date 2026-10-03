package com.project.Xclone_backend.post;

/** Who may reply in a conversation. Set on the top-level post; the author can always reply. */
public enum ReplyPolicy {
    EVERYONE,
    /** Only accounts the author follows. */
    FOLLOWING,
    /** Only accounts @mentioned in the top-level post. */
    MENTIONED
}
