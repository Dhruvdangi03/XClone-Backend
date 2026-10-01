package com.project.Xclone_backend.hashtag;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HashtagService {

    /**
     * '#' then letters, digits or '_', with at least one letter (so "#2024" is not a tag). Must not follow a word
     * character, '#', '&' or '/', which skips "abc#x", "##x", HTML entities like "&#39;" and URL fragments.
     */
    private static final Pattern HASHTAG =
            Pattern.compile("(?<![\\p{L}\\p{N}_#&/])#([\\p{L}\\p{N}_]*\\p{L}[\\p{L}\\p{N}_]*)");

    private final HashtagRepository hashtagRepository;

    /** Distinct normalized tags in order of first appearance. */
    public static Set<String> extract(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null) {
            return names;
        }
        Matcher m = HASHTAG.matcher(content);
        while (m.find()) {
            names.add(normalize(m.group(1)));
        }
        return names;
    }

    /** Lowercases and drops a leading '#', so "#Java", "java" and "JAVA" all become "java". */
    public static String normalize(String raw) {
        String name = raw.strip();
        if (name.startsWith("#")) {
            name = name.substring(1);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /** Returns the managed hashtags for the tags in {@code content}, creating any that don't exist yet. */
    @Transactional
    public Set<Hashtag> resolve(String content) {
        Set<String> names = extract(content);
        if (names.isEmpty()) {
            return Set.of();
        }
        names.forEach(hashtagRepository::insertIfAbsent);
        return new HashSet<>(hashtagRepository.findByNameIn(names));
    }
}
