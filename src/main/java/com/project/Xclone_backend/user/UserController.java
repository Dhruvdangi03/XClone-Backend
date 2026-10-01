package com.project.Xclone_backend.user;

import java.util.List;

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
import com.project.Xclone_backend.security.AuthUser;
import com.project.Xclone_backend.user.UserDtos.ProfileResponse;
import com.project.Xclone_backend.user.UserDtos.UpdateProfileRequest;
import com.project.Xclone_backend.user.UserDtos.UserResponse;
import com.project.Xclone_backend.user.UserDtos.UserSummary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser me) {
        return userService.me(me.id());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(me.id(), req);
    }

    @GetMapping("/me/blocks")
    public CursorPage<UserSummary> blocked(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return userService.blocked(me.id(), cursor, limit);
    }

    @GetMapping("/search")
    public List<UserSummary> search(@RequestParam(name = "q", required = false) String q) {
        return userService.search(q);
    }

    @GetMapping("/{username}")
    public ProfileResponse profile(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        return userService.profile(username, me == null ? null : me.id());
    }

    @PostMapping("/{username}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        userService.follow(me.id(), username);
    }

    @DeleteMapping("/{username}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        userService.unfollow(me.id(), username);
    }

    @PostMapping("/{username}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        userService.block(me.id(), username);
    }

    @DeleteMapping("/{username}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable String username, @AuthenticationPrincipal AuthUser me) {
        userService.unblock(me.id(), username);
    }

    @GetMapping("/{username}/followers")
    public CursorPage<UserSummary> followers(@PathVariable String username, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return userService.followers(username, me == null ? null : me.id(), cursor, limit);
    }

    @GetMapping("/{username}/following")
    public CursorPage<UserSummary> following(@PathVariable String username, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return userService.following(username, me == null ? null : me.id(), cursor, limit);
    }
}
