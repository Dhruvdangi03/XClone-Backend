package com.project.Xclone_backend.post;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.post.PostDtos.CreatePostRequest;
import com.project.Xclone_backend.post.PostDtos.PostResponse;
import com.project.Xclone_backend.post.PostDtos.UpdatePostRequest;
import com.project.Xclone_backend.report.ReportDtos.ReportRequest;
import com.project.Xclone_backend.security.AuthUser;
import com.project.Xclone_backend.user.UserDtos.UserSummary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreatePostRequest req) {
        return postService.create(me.id(), req);
    }

    @GetMapping("/posts/{id}")
    public PostResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return postService.get(id, idOf(me));
    }

    @PatchMapping("/posts/{id}")
    public PostResponse update(@PathVariable Long id, @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody UpdatePostRequest req) {
        return postService.update(id, me.id(), req);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        postService.delete(id, me.id());
    }

    @GetMapping("/posts/{id}/replies")
    public CursorPage<PostResponse> replies(@PathVariable Long id, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return postService.replies(id, idOf(me), cursor, limit);
    }

    @PostMapping("/posts/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable Long id, @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody ReportRequest req) {
        postService.report(id, me.id(), req.reason());
    }

    @PostMapping("/posts/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        postService.like(id, me.id());
    }

    @DeleteMapping("/posts/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        postService.unlike(id, me.id());
    }

    @GetMapping("/posts/{id}/likes")
    public CursorPage<UserSummary> likers(@PathVariable Long id,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return postService.likers(id, cursor, limit);
    }

    @GetMapping("/users/{username}/posts")
    public CursorPage<PostResponse> userPosts(@PathVariable String username, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return postService.userPosts(username, idOf(me), cursor, limit);
    }

    @GetMapping("/users/{username}/replies")
    public CursorPage<PostResponse> userReplies(@PathVariable String username, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return postService.userReplies(username, idOf(me), cursor, limit);
    }

    @GetMapping("/users/{username}/likes")
    public CursorPage<PostResponse> userLikes(@PathVariable String username, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return postService.userLikes(username, idOf(me), cursor, limit);
    }

    @GetMapping("/hashtags/{name}/posts")
    public CursorPage<PostResponse> hashtagPosts(@PathVariable String name, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return postService.hashtagPosts(name, idOf(me), cursor, limit);
    }

    @GetMapping("/timeline")
    public CursorPage<PostResponse> timeline(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return postService.timeline(me.id(), cursor, limit);
    }

    private static Long idOf(AuthUser me) {
        return me == null ? null : me.id();
    }
}
