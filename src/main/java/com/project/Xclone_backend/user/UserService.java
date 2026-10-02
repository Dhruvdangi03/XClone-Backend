package com.project.Xclone_backend.user;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.auth.RefreshTokenRepository;
import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.follow.Follow;
import com.project.Xclone_backend.follow.FollowRepository;
import com.project.Xclone_backend.bookmark.BookmarkRepository;
import com.project.Xclone_backend.like.LikeRepository;
import com.project.Xclone_backend.media.MediaService;
import com.project.Xclone_backend.post.PostRepository;
import com.project.Xclone_backend.report.ReportReason;
import com.project.Xclone_backend.report.UserReportRepository;
import com.project.Xclone_backend.user.UserDtos.ChangeEmailRequest;
import com.project.Xclone_backend.user.UserDtos.ChangePasswordRequest;
import com.project.Xclone_backend.user.UserDtos.ChangeUsernameRequest;
import com.project.Xclone_backend.user.UserDtos.DeleteAccountRequest;
import com.project.Xclone_backend.user.UserDtos.ProfileResponse;
import com.project.Xclone_backend.user.UserDtos.UpdateProfileRequest;
import com.project.Xclone_backend.user.UserDtos.UserResponse;
import com.project.Xclone_backend.user.UserDtos.UserSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int SEARCH_LIMIT = 20;

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final UserMapper userMapper;
    private final MediaService mediaService;
    private final UserReportRepository userReportRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;

    public User requireByUsername(String username) {
        return userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
                .filter(u -> u.getStatus() != AccountStatus.DELETED)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    public User requireById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userMapper.toResponse(requireById(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = requireById(userId);
        if (req.displayName() != null) {
            String name = req.displayName().strip();
            if (name.isEmpty()) {
                throw ApiException.badRequest("Display name cannot be blank");
            }
            user.setDisplayName(name);
        }
        if (req.bio() != null) {
            user.setBio(req.bio().isBlank() ? null : req.bio().strip());
        }
        if (req.avatarKey() != null) {
            user.setAvatarKey(resolveMediaKey(userId, req.avatarKey()));
        }
        if (req.bannerKey() != null) {
            user.setBannerKey(resolveMediaKey(userId, req.bannerKey()));
        }
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse changeUsername(Long userId, ChangeUsernameRequest req) {
        User user = requireById(userId);
        String username = req.username().toLowerCase(Locale.ROOT);
        if (!username.equals(user.getUsername())) {
            if (userRepository.existsByUsername(username)) {
                throw ApiException.conflict("Username is already taken");
            }
            user.setUsername(username);
        }
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse changeEmail(Long userId, ChangeEmailRequest req) {
        User user = requireById(userId);
        String email = req.email().toLowerCase(Locale.ROOT);
        if (!email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw ApiException.conflict("Email is already registered");
            }
            user.setEmail(email);
        }
        return userMapper.toResponse(user);
    }

    /** Also signs out every session: refresh tokens are revoked, so each device must log in again. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = requireById(userId);
        requirePassword(user, req.currentPassword());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokenRepository.revokeAllForUser(userId);
    }

    /** Nothing is deleted and all sessions end; logging in with the correct password reactivates the account. */
    @Transactional
    public void deactivate(Long userId) {
        User user = requireById(userId);
        user.setStatus(AccountStatus.DEACTIVATED);
        refreshTokenRepository.revokeAllForUser(userId);
    }

    /**
     * Removes the user's personal data, likes, follows, sessions and post content. The row itself stays as an
     * anonymized placeholder because other users' replies and reports reference it and its posts.
     */
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest req) {
        User user = requireById(userId);
        requirePassword(user, req.password());

        postRepository.decrementReplyCountsForAuthor(userId);
        postRepository.decrementLikeCountsForLiker(userId);
        postRepository.decrementRepostCountsForReposter(userId);
        likeRepository.deleteAllByUser(userId);
        bookmarkRepository.deleteAllByUser(userId);
        followRepository.deleteAllInvolving(userId);
        refreshTokenRepository.deleteAllForUser(userId);
        postRepository.deleteHashtagLinksForAuthor(userId);
        postRepository.deleteMentionLinksInvolving(userId);
        postRepository.deleteMediaForAuthor(userId);
        postRepository.softDeleteAndClearAllByAuthor(userId);

        // '~' is not allowed in usernames, so these can never collide with a real account.
        user.setUsername("~" + userId);
        user.setEmail("~" + userId + "@deleted.invalid");
        user.setPasswordHash("!");
        user.setDisplayName("Deleted user");
        user.setBio(null);
        user.setAvatarKey(null);
        user.setBannerKey(null);
        user.setStatus(AccountStatus.DELETED);
    }

    private void requirePassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
    }

    private String resolveMediaKey(Long userId, String key) {
        if (key.isEmpty()) {
            return null;
        }
        mediaService.verifyOwnedUpload(userId, key);
        return key;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(String username, Long viewerId) {
        User user = requireByUsername(username);
        boolean followedByMe = viewerId != null
                && followRepository.existsByFollowerIdAndFolloweeId(viewerId, user.getId());
        return userMapper.toProfile(user,
                followRepository.countByFolloweeId(user.getId()),
                followRepository.countByFollowerId(user.getId()),
                followedByMe);
    }

    @Transactional
    public void follow(Long followerId, String username) {
        User target = requireByUsername(username);
        if (target.getId().equals(followerId)) {
            throw ApiException.badRequest("You cannot follow yourself");
        }
        followRepository.follow(followerId, target.getId());
    }

    /** Only records the report; the reported user is not changed in any way. */
    @Transactional
    public void report(Long reporterId, String username, ReportReason reason) {
        User target = requireByUsername(username);
        if (target.getId().equals(reporterId)) {
            throw ApiException.badRequest("You cannot report yourself");
        }
        if (userReportRepository.report(reporterId, target.getId(), reason.name()) == 0) {
            throw ApiException.conflict("You have already reported this user");
        }
    }

    @Transactional
    public void unfollow(Long followerId, String username) {
        User target = requireByUsername(username);
        followRepository.unfollow(followerId, target.getId());
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> followers(String username, Long cursor, Integer limit) {
        User user = requireByUsername(username);
        int n = CursorPage.clampLimit(limit);
        List<Follow> rows = followRepository.findFollowers(user.getId(), CursorPage.cursorOrMax(cursor), Limit.of(n + 1));
        return CursorPage.of(rows, n, Follow::getId,
                page -> page.stream().map(f -> userMapper.toSummary(f.getFollower())).toList());
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> following(String username, Long cursor, Integer limit) {
        User user = requireByUsername(username);
        int n = CursorPage.clampLimit(limit);
        List<Follow> rows = followRepository.findFollowing(user.getId(), CursorPage.cursorOrMax(cursor), Limit.of(n + 1));
        return CursorPage.of(rows, n, Follow::getId,
                page -> page.stream().map(f -> userMapper.toSummary(f.getFollowee())).toList());
    }

    @Transactional(readOnly = true)
    public List<UserSummary> search(String q) {
        String term = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        if (term.startsWith("@")) {
            term = term.substring(1);
        }
        if (term.isEmpty()) {
            return List.of();
        }
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return userRepository.search(escaped + "%", Limit.of(SEARCH_LIMIT)).stream()
                .map(userMapper::toSummary)
                .toList();
    }
}
