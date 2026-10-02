package com.project.Xclone_backend.post;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.project.Xclone_backend.config.R2Properties;
import com.project.Xclone_backend.like.LikeRepository;
import com.project.Xclone_backend.post.PostDtos.PostResponse;
import com.project.Xclone_backend.user.UserDtos.UserSummary;
import com.project.Xclone_backend.user.User;
import com.project.Xclone_backend.user.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PostMapper {

    private final UserMapper userMapper;
    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final R2Properties r2;

    public PostResponse toResponse(Post post, Long viewerId) {
        return toResponses(List.of(post), viewerId).get(0);
    }

    /**
     * Maps a page of posts, resolving "liked/reposted by me" with one query each. Authors (and, for repost rows, the
     * original and its author) must already be fetched. A repost row is rendered as its original.
     */
    public List<PostResponse> toResponses(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Post> targets = posts.stream().map(PostMapper::target).toList();
        // Quoted posts, keyed by id. Deleted ones are absent, so their quote posts render with quotedPost = null.
        List<Long> quotedIds = targets.stream().filter(t -> t.getQuoteOf() != null)
                .map(t -> t.getQuoteOf().getId()).distinct().toList();
        Map<Long, Post> quoted = quotedIds.isEmpty() ? Map.of()
                : postRepository.findLiveByIds(quotedIds).stream().collect(Collectors.toMap(Post::getId, q -> q));

        List<Long> targetIds = Stream.concat(targets.stream().map(Post::getId), quoted.keySet().stream())
                .distinct().toList();
        Set<Long> liked = viewerId == null ? Set.of() : likeRepository.findLikedPostIds(viewerId, targetIds);
        Set<Long> reposted = viewerId == null ? Set.of() : postRepository.findRepostedPostIds(viewerId, targetIds);
        return posts.stream().map(p -> {
            Post t = target(p);
            UserSummary repostedBy = p.getRepostOf() == null ? null : userMapper.toSummary(p.getAuthor());
            Post q = t.getQuoteOf() == null ? null : quoted.get(t.getQuoteOf().getId());
            PostResponse quotedResponse = q == null ? null
                    : map(q, liked.contains(q.getId()), reposted.contains(q.getId()), null, null);
            return map(t, liked.contains(t.getId()), reposted.contains(t.getId()), repostedBy, quotedResponse);
        }).toList();
    }

    private static Post target(Post p) {
        return p.getRepostOf() == null ? p : p.getRepostOf();
    }

    private PostResponse map(Post p, boolean likedByMe, boolean repostedByMe, UserSummary repostedBy,
            PostResponse quotedPost) {
        List<UserSummary> mentions = p.getMentions().stream()
                .sorted(Comparator.comparing(User::getUsername)).map(userMapper::toSummary).toList();
        List<String> mediaUrls = p.getMedia().stream().map(m -> r2.publicUrl(m.getR2Key())).toList();
        Long replyToId = p.getParent() == null ? null : p.getParent().getId();
        return new PostResponse(p.getId(), userMapper.toSummary(p.getAuthor()), p.getContent(), mediaUrls,
                replyToId, p.getLikeCount(), p.getReplyCount(), likedByMe, p.getCreatedAt(),
                p.getRepostCount(), repostedByMe, repostedBy, quotedPost, mentions);
    }
}
