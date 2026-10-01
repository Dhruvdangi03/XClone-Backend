package com.project.Xclone_backend.hashtag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashtagServiceTest {

    @Test
    void extractsDistinctLowercaseTagsInOrder() {
        assertThat(HashtagService.extract("Learning #Java #SpringBoot #Java and #JAVA"))
                .containsExactly("java", "springboot");
    }

    @Test
    void trailingPunctuationEndsTheTag() {
        assertThat(HashtagService.extract("#Java, #spring_boot! (#react) #Java's"))
                .containsExactly("java", "spring_boot", "react");
    }

    @Test
    void requiresAtLeastOneLetter() {
        assertThat(HashtagService.extract("#2024 #123 #java17 #_ #")).containsExactly("java17");
    }

    @Test
    void ignoresTagsGluedToWordsUrlsAndEntities() {
        assertThat(HashtagService.extract("abc#tag ##double https://site.com/#frag page#x it&#39;s"))
                .isEmpty();
    }

    @Test
    void supportsUnicodeLetters() {
        assertThat(HashtagService.extract("#Café #日本")).containsExactly("café", "日本");
    }

    @Test
    void emptyOrNullContentHasNoTags() {
        assertThat(HashtagService.extract(null)).isEmpty();
        assertThat(HashtagService.extract("")).isEmpty();
        assertThat(HashtagService.extract("no tags here")).isEmpty();
    }

    @Test
    void normalizeStripsHashAndLowercases() {
        assertThat(HashtagService.normalize("#Java")).isEqualTo("java");
        assertThat(HashtagService.normalize("JAVA")).isEqualTo("java");
    }
}
