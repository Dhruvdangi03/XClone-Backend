package com.project.Xclone_backend.user;

public enum AccountStatus {
    ACTIVE,
    /** The row and all content are kept; tokens stop working until the user logs in again, which reactivates it. */
    DEACTIVATED,
    /** The row is an anonymized placeholder kept so other users' threads and reports stay intact. */
    DELETED
}
