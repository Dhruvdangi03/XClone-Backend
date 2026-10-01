package com.project.Xclone_backend.user;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.follow.Follow;
import com.project.Xclone_backend.follow.FollowRepository;
import com.project.Xclone_backend.media.MediaService;
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

    public User requireByUsername(String username) {
        return userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
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
