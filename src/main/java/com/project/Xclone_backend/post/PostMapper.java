package com.project.Xclone_backend.post;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.project.Xclone_backend.config.R2Properties;
import com.project.Xclone_backend.follow.FollowRepository;
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
    private final FollowRepository followRepository;
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
        ReplyAccess access = replyAccess(Stream.concat(targets.stream(), quoted.values().stream()).toList(), viewerId);
        return posts.stream().map(p -> {
            Post t = target(p);
            UserSummary repostedBy = p.getRepostOf() == null ? null : userMapper.toSummary(p.getAuthor());
            Post q = t.getQuoteOf() == null ? null : quoted.get(t.getQuoteOf().getId());
            PostResponse quotedResponse = q == null ? null
                    : map(q, liked.contains(q.getId()), reposted.contains(q.getId()), null, null, access);
            return map(t, liked.contains(t.getId()), reposted.contains(t.getId()), repostedBy, quotedResponse, access);
        }).toList();
    }

    /**
     * Resolves each post's conversation root (replies point at it; top-level posts are their own root) with one query
     * for the roots not already in hand, and works out which of those conversations the viewer may reply to.
     */
    private ReplyAccess replyAccess(List<Post> posts, Long viewerId) {
        Map<Long, Post> roots = new HashMap<>();
        posts.forEach(p -> {
            if (p.getRoot() == null) {
                roots.put(p.getId(), p);
            }
        });
        List<Long> missing = posts.stream().filter(p -> p.getRoot() != null).map(p -> p.getRoot().getId())
                .filter(id -> !roots.containsKey(id)).distinct().toList();
        if (!missing.isEmpty()) {
            postRepository.findRootsByIds(missing).forEach(r -> roots.put(r.getId(), r));
        }
        Set<Long> followingViewer = Set.of();
        if (viewerId != null) {
            List<Long> authorIds = roots.values().stream()
                    .filter(r -> r.getReplyPolicy() == ReplyPolicy.FOLLOWING).map(r -> r.getAuthor().getId())
                    .distinct().toList();
            followingViewer = authorIds.isEmpty() ? Set.of()
                    : followRepository.findFollowerIdsAmong(viewerId, authorIds);
        }
        return new ReplyAccess(roots, viewerId, followingViewer);
    }

    private record ReplyAccess(Map<Long, Post> roots, Long viewerId, Set<Long> authorsFollowingViewer) {

        Post rootOf(Post p) {
            return p.getRoot() == null ? p : roots.get(p.getRoot().getId());
        }

        boolean canReply(Post root) {
            if (viewerId == null || root == null) {
                return false;
            }
            if (root.getAuthor().getId().equals(viewerId)) {
                return true;
            }
            return switch (root.getReplyPolicy()) {
                case EVERYONE -> true;
                case FOLLOWING -> authorsFollowingViewer.contains(root.getAuthor().getId());
                case MENTIONED -> root.getMentions().stream().anyMatch(u -> u.getId().equals(viewerId));
            };
        }
    }

    private static Post target(Post p) {
        return p.getRepostOf() == null ? p : p.getRepostOf();
    }

    private PostResponse map(Post p, boolean likedByMe, boolean repostedByMe, UserSummary repostedBy,
            PostResponse quotedPost, ReplyAccess access) {
        List<UserSummary> mentions = p.getMentions().stream()
                .sorted(Comparator.comparing(User::getUsername)).map(userMapper::toSummary).toList();
        List<String> mediaUrls = p.getMedia().stream().map(m -> r2.publicUrl(m.getR2Key())).toList();
        Long replyToId = p.getParent() == null ? null : p.getParent().getId();
        Post root = access.rootOf(p);
        return new PostResponse(p.getId(), userMapper.toSummary(p.getAuthor()), p.getContent(), mediaUrls,
                replyToId, p.getLikeCount(), p.getReplyCount(), likedByMe, p.getCreatedAt(),
                p.getRepostCount(), repostedByMe, repostedBy, quotedPost, mentions,
                p.getRoot() == null ? p.getId() : p.getRoot().getId(),
                root == null ? ReplyPolicy.EVERYONE : root.getReplyPolicy(), access.canReply(root));
    }
}
