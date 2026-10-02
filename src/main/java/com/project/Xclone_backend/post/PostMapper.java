package com.project.Xclone_backend.post;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.project.Xclone_backend.config.R2Properties;
import com.project.Xclone_backend.like.LikeRepository;
import com.project.Xclone_backend.post.PostDtos.PostResponse;
import com.project.Xclone_backend.user.UserDtos.UserSummary;
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
        List<Long> targetIds = posts.stream().map(p -> target(p).getId()).distinct().toList();
        Set<Long> liked = viewerId == null ? Set.of() : likeRepository.findLikedPostIds(viewerId, targetIds);
        Set<Long> reposted = viewerId == null ? Set.of() : postRepository.findRepostedPostIds(viewerId, targetIds);
        return posts.stream().map(p -> {
            Post t = target(p);
            UserSummary repostedBy = p.getRepostOf() == null ? null : userMapper.toSummary(p.getAuthor());
            return map(t, liked.contains(t.getId()), reposted.contains(t.getId()), repostedBy);
        }).toList();
    }

    private static Post target(Post p) {
        return p.getRepostOf() == null ? p : p.getRepostOf();
    }

    private PostResponse map(Post p, boolean likedByMe, boolean repostedByMe, UserSummary repostedBy) {
        List<String> mediaUrls = p.getMedia().stream().map(m -> r2.publicUrl(m.getR2Key())).toList();
        Long replyToId = p.getParent() == null ? null : p.getParent().getId();
        return new PostResponse(p.getId(), userMapper.toSummary(p.getAuthor()), p.getContent(), mediaUrls,
                replyToId, p.getLikeCount(), p.getReplyCount(), likedByMe, p.getCreatedAt(),
                p.getRepostCount(), repostedByMe, repostedBy);
    }
}
