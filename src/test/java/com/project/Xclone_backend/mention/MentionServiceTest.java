package com.project.Xclone_backend.mention;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MentionServiceTest {

    @Test
    void extractsDistinctLowercaseUsernamesInOrder() {
        assertThat(MentionService.extract("Hi @Alice and @bob_1, cc @ALICE")).containsExactly("alice", "bob_1");
    }

    @Test
    void punctuationEndsTheMention() {
        assertThat(MentionService.extract("(@alice) @bob! @carol. @dave's")).containsExactly("alice", "bob", "carol", "dave");
    }

    @Test
    void ignoresEmailsUrlsAndGluedHandles() {
        assertThat(MentionService.extract("mail me@example.com or https://x.com/@alice or abc@def @@ghi")).isEmpty();
    }

    @Test
    void ignoresInvalidUsernameLengths() {
        assertThat(MentionService.extract("@ab @abcdefghijklmnop @ @")).isEmpty();
        assertThat(MentionService.extract("@abc @abcdefghijklmno")).containsExactly("abc", "abcdefghijklmno");
    }

    @Test
    void emptyOrNullContentHasNoMentions() {
        assertThat(MentionService.extract(null)).isEmpty();
        assertThat(MentionService.extract("")).isEmpty();
        assertThat(MentionService.extract("no mentions here")).isEmpty();
    }
}
