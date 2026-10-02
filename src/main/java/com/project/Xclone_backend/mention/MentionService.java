package com.project.Xclone_backend.mention;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.user.AccountStatus;
import com.project.Xclone_backend.user.User;
import com.project.Xclone_backend.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MentionService {

    /**
     * '@' then a valid username (3-15 letters, digits or '_'). Must not follow a word character, '@', '.', '/' or
     * '&', and must not run into more username characters, which skips emails ("a@b.com"), "@@x", URLs and handles
     * longer than 15 characters instead of cutting them short.
     */
    private static final Pattern MENTION =
            Pattern.compile("(?<![A-Za-z0-9_@./&])@([A-Za-z0-9_]{3,15})(?![A-Za-z0-9_])");

    private final UserRepository userRepository;

    /** Distinct lowercase usernames in order of first appearance. */
    public static Set<String> extract(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null) {
            return names;
        }
        Matcher m = MENTION.matcher(content);
        while (m.find()) {
            names.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /**
     * Returns the users mentioned in {@code content}. Handles that match no active account are plain text, not
     * mentions: they are skipped rather than rejected, so deactivated and deleted accounts are never linked.
     */
    @Transactional(readOnly = true)
    public Set<User> resolve(String content) {
        Set<String> names = extract(content);
        if (names.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(userRepository.findByUsernameInAndStatus(names, AccountStatus.ACTIVE));
    }
}
