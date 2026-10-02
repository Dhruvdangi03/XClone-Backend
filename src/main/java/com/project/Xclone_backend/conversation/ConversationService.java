package com.project.Xclone_backend.conversation;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.conversation.ConversationDtos.ConversationResponse;
import com.project.Xclone_backend.user.AccountStatus;
import com.project.Xclone_backend.user.User;
import com.project.Xclone_backend.user.UserMapper;
import com.project.Xclone_backend.user.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final UserMapper userMapper;

    public record Result(ConversationResponse conversation, boolean created) {
    }

    @Transactional
    public Result getOrCreate(Long meId, String username) {
        User other = userService.requireByUsername(username);
        if (other.getId().equals(meId)) {
            throw ApiException.badRequest("You cannot message yourself");
        }
        requireMessageable(meId, other);

        Long low = Math.min(meId, other.getId());
        Long high = Math.max(meId, other.getId());
        boolean created = conversationRepository.createIfAbsent(low, high) > 0;
        Conversation c = conversationRepository.findByPair(low, high)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        return new Result(toResponse(c, meId), created);
    }

    @Transactional(readOnly = true)
    public CursorPage<ConversationResponse> list(Long meId, Long cursor, Integer limit) {
        int size = CursorPage.clampLimit(limit);
        var rows = conversationRepository.findPage(meId, CursorPage.cursorOrMax(cursor), Limit.of(size + 1));
        return CursorPage.of(rows, size, Conversation::getId, page -> page.stream().map(c -> toResponse(c, meId)).toList());
    }

    /** Non-participants get a 404 so the existence of other users' conversations is not revealed. */
    @Transactional(readOnly = true)
    public ConversationResponse get(Long meId, Long id) {
        return toResponse(requireAccessible(meId, id), meId);
    }

    /** The caller must participate (else 404) and the other participant must be reachable (else 403). */
    public Conversation requireAccessible(Long meId, Long id) {
        Conversation c = conversationRepository.findByIdAndParticipant(id, meId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        requireMessageable(meId, other(c, meId));
        return c;
    }

    private void requireMessageable(Long meId, User other) {
        if (other.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("This user cannot receive messages");
        }
        userService.requireNotBlocked(meId, other.getId());
    }

    private static User other(Conversation c, Long meId) {
        return c.getUserOne().getId().equals(meId) ? c.getUserTwo() : c.getUserOne();
    }

    private ConversationResponse toResponse(Conversation c, Long meId) {
        return new ConversationResponse(c.getId(), userMapper.toSummary(other(c, meId)), c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
