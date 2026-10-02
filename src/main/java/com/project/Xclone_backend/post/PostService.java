package com.project.Xclone_backend.post;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.hashtag.Hashtag;
import com.project.Xclone_backend.hashtag.HashtagService;
import com.project.Xclone_backend.like.LikeRepository;
import com.project.Xclone_backend.like.PostLike;
import com.project.Xclone_backend.media.MediaService;
import com.project.Xclone_backend.post.PostDtos.CreatePostRequest;
import com.project.Xclone_backend.post.PostDtos.PostResponse;
import com.project.Xclone_backend.post.PostDtos.UpdatePostRequest;
import com.project.Xclone_backend.report.PostReportRepository;
import com.project.Xclone_backend.report.ReportReason;
import com.project.Xclone_backend.user.User;
import com.project.Xclone_backend.user.UserDtos.UserSummary;
import com.project.Xclone_backend.user.UserMapper;
import com.project.Xclone_backend.user.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final UserService userService;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final MediaService mediaService;
    private final HashtagService hashtagService;
    private final PostReportRepository postReportRepository;

    @Transactional
    public PostResponse create(Long authorId, CreatePostRequest req) {
        String content = req.content() == null ? "" : req.content().strip();
        List<String> mediaKeys = req.mediaKeys() == null ? List.of() : List.copyOf(new LinkedHashSet<>(req.mediaKeys()));
        if (content.isEmpty() && mediaKeys.isEmpty()) {
            throw ApiException.badRequest("A post needs text or at least one image");
        }
        mediaKeys.forEach(key -> mediaService.verifyOwnedUpload(authorId, key));

        Post post = new Post();
        post.setAuthor(userService.requireById(authorId));
        post.setContent(content);
        mediaKeys.forEach(post::addMedia);
        syncHashtags(post);

        if (req.replyToId() != null) {
            Post parent = requireLive(req.replyToId());
            userService.requireNotBlocked(authorId, parent.getAuthor().getId());
            post.setParent(parent);
            postRepository.addToReplyCount(parent.getId(), 1);
        }
        postRepository.save(post);
        return postMapper.toResponse(post, authorId);
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long postId, Long viewerId) {
        Post post = requireLive(postId);
        requireVisible(viewerId, post.getAuthor());
        return postMapper.toResponse(post, viewerId);
    }

    @Transactional
    public PostResponse update(Long postId, Long userId, UpdatePostRequest req) {
        Post post = requireLive(postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("You can only edit your own posts");
        }
        post.setContent(req.content().strip());
        syncHashtags(post);
        return postMapper.toResponse(post, userId);
    }

    @Transactional
    public void delete(Long postId, Long userId) {
        Post post = requireLive(postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("You can only delete your own posts");
        }
        post.setDeleted(true);
        if (post.getParent() != null) {
            postRepository.addToReplyCount(post.getParent().getId(), -1);
        }
    }

    /** Only records the report; the reported post is not changed in any way. */
    @Transactional
    public void report(Long postId, Long reporterId, ReportReason reason) {
        Post post = requireLive(postId);
        if (post.getAuthor().getId().equals(reporterId)) {
            throw ApiException.badRequest("You cannot report your own post");
        }
        if (postReportRepository.report(reporterId, postId, reason.name()) == 0) {
            throw ApiException.conflict("You have already reported this post");
        }
    }

    @Transactional
    public void repost(Long postId, Long userId) {
        Post post = requireLive(postId);
        if (post.getAuthor().getId().equals(userId)) {
            throw ApiException.badRequest("You cannot repost your own post");
        }
        if (postRepository.repost(userId, postId) > 0) {
            postRepository.addToRepostCount(postId, 1);
        }
    }

    @Transactional
    public void unrepost(Long postId, Long userId) {
        requireLive(postId);
        if (postRepository.unrepost(userId, postId) > 0) {
            postRepository.addToRepostCount(postId, -1);
        }
    }

    @Transactional
    public void like(Long postId, Long userId) {
        Post post = requireLive(postId);
        userService.requireNotBlocked(userId, post.getAuthor().getId());
        if (likeRepository.like(userId, postId) > 0) {
            postRepository.addToLikeCount(postId, 1);
        }
    }

    @Transactional
    public void unlike(Long postId, Long userId) {
        requireLive(postId);
        if (likeRepository.unlike(userId, postId) > 0) {
            postRepository.addToLikeCount(postId, -1);
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> replies(Long postId, Long viewerId, Long cursor, Integer limit) {
        requireLive(postId);
        int n = CursorPage.clampLimit(limit);
        List<Post> rows = postRepository.findReplies(postId, viewerId, cursor == null ? 0L : cursor,
                Limit.of(n + 1));
        return CursorPage.of(rows, n, Post::getId, page -> postMapper.toResponses(page, viewerId));
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> likers(Long postId, Long cursor, Integer limit) {
        requireLive(postId);
        int n = CursorPage.clampLimit(limit);
        List<PostLike> rows = likeRepository.findLikers(postId, CursorPage.cursorOrMax(cursor), Limit.of(n + 1));
        return CursorPage.of(rows, n, PostLike::getId,
                page -> page.stream().map(l -> userMapper.toSummary(l.getUser())).toList());
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> userPosts(String username, Long viewerId, Long cursor, Integer limit) {
        User user = userService.requireByUsername(username);
        requireVisible(viewerId, user);
        int n = CursorPage.clampLimit(limit);
        List<Post> rows = postRepository.findUserPosts(user.getId(), CursorPage.cursorOrMax(cursor), Limit.of(n + 1));
        return CursorPage.of(rows, n, Post::getId, page -> postMapper.toResponses(page, viewerId));
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> userReplies(String username, Long viewerId, Long cursor, Integer limit) {
        User user = userService.requireByUsername(username);
        requireVisible(viewerId, user);
        int n = CursorPage.clampLimit(limit);
        List<Post> rows = postRepository.findUserReplies(user.getId(), CursorPage.cursorOrMax(cursor), Limit.of(n + 1));
        return CursorPage.of(rows, n, Post::getId, page -> postMapper.toResponses(page, viewerId));
    }

    /** Cursor here is the like id, so paging follows "most recently liked" order. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> userLikes(String username, Long viewerId, Long cursor, Integer limit) {
        User user = userService.requireByUsername(username);
        requireVisible(viewerId, user);
        int n = CursorPage.clampLimit(limit);
        List<PostLike> rows = likeRepository.findUserLikes(user.getId(), viewerId, CursorPage.cursorOrMax(cursor),
                Limit.of(n + 1));
        return CursorPage.of(rows, n, PostLike::getId,
                page -> postMapper.toResponses(page.stream().map(PostLike::getPost).toList(), viewerId));
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> hashtagPosts(String name, Long viewerId, Long cursor, Integer limit) {
        int n = CursorPage.clampLimit(limit);
        List<Post> rows = postRepository.findByHashtag(HashtagService.normalize(name), CursorPage.cursorOrMax(cursor),
                Limit.of(n + 1));
        return CursorPage.of(rows, n, Post::getId, page -> postMapper.toResponses(page, viewerId));
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> timeline(Long userId, Long cursor, Integer limit) {
        int n = CursorPage.clampLimit(limit);
        List<Post> rows = postRepository.findTimeline(userId, CursorPage.cursorOrMax(cursor), Limit.of(n + 1));
        return CursorPage.of(rows, n, Post::getId, page -> postMapper.toResponses(page, userId));
    }

    private void requireVisible(Long viewerId, User user) {
        if (viewerId != null) {
            userService.requireNotBlocked(viewerId, user.getId());
        }
    }
  
    /** Retain-then-add so Hibernate only writes the link rows that actually changed. */
    private void syncHashtags(Post post) {
        Set<Hashtag> tags = hashtagService.resolve(post.getContent());
        post.getHashtags().retainAll(tags);
        post.getHashtags().addAll(tags);
    }

    private Post requireLive(Long postId) {
        return postRepository.findLive(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
    }
}
