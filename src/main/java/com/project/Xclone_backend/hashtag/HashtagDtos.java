package com.project.Xclone_backend.hashtag;

public final class HashtagDtos {

    private HashtagDtos() {
    }

    /** {@code name} is lowercase without the leading '#'. */
    public record TrendingHashtag(String name, long postCount, long userCount) {
    }
}
