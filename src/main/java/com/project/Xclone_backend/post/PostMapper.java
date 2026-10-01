package com.project.Xclone_backend.post;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.project.Xclone_backend.config.R2Properties;
import com.project.Xclone_backend.like.LikeRepository;
import com.project.Xclone_backend.post.PostDtos.PostResponse;
import com.project.Xclone_backend.user.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PostMapper {

    private final UserMapper userMapper;
    private final LikeRepository likeRepository;
    private final R2Properties r2;

    public PostResponse toResponse(Post post, Long viewerId) {
        return toResponses(List.of(post), viewerId).get(0);
    }

    /** Maps a page of posts, resolving "liked by me" with a single query. Authors must already be fetched. */
    public List<PostResponse> toResponses(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> liked = viewerId == null
                ? Set.of()
                : likeRepository.findLikedPostIds(viewerId, posts.stream().map(Post::getId).toList());
        return posts.stream().map(p -> map(p, liked.contains(p.getId()))).toList();
    }

    private PostResponse map(Post p, boolean likedByMe) {
        List<String> mediaUrls = p.getMedia().stream().map(m -> r2.publicUrl(m.getR2Key())).toList();
        Long replyToId = p.getParent() == null ? null : p.getParent().getId();
        return new PostResponse(p.getId(), userMapper.toSummary(p.getAuthor()), p.getContent(), mediaUrls,
                replyToId, p.getLikeCount(), p.getReplyCount(), likedByMe, p.getCreatedAt());
    }
}
