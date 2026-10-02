package com.project.Xclone_backend.message;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.conversation.Conversation;
import com.project.Xclone_backend.conversation.ConversationRepository;
import com.project.Xclone_backend.conversation.ConversationService;
import com.project.Xclone_backend.message.MessageDtos.MessageResponse;
import com.project.Xclone_backend.message.MessageDtos.UnreadCountResponse;
import com.project.Xclone_backend.user.UserMapper;
import com.project.Xclone_backend.user.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final UserService userService;
    private final UserMapper userMapper;

    @Transactional
    public MessageResponse send(Long meId, Long conversationId, String content) {
        Conversation conversation = conversationService.requireAccessible(meId, conversationId);
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(userService.requireById(meId));
        message.setContent(content.strip());
        messageRepository.save(message);
        conversationRepository.touch(conversationId, Instant.now());
        return toResponse(message);
    }

    /**
     * Pages walk backwards through history: each page is ordered oldest to newest for display, and
     * {@code nextCursor} (the oldest id in the page) loads the next older page.
     */
    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> list(Long meId, Long conversationId, Long cursor, Integer limit) {
        conversationService.requireAccessible(meId, conversationId);
        int size = CursorPage.clampLimit(limit);
        List<Message> rows = messageRepository.findPage(conversationId, CursorPage.cursorOrMax(cursor),
                Limit.of(size + 1));
        return CursorPage.of(rows, size, Message::getId,
                page -> page.stream().map(this::toResponse).collect(Collectors.collectingAndThen(Collectors.toList(), l -> {
                    Collections.reverse(l);
                    return l;
                })));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long meId, Long conversationId) {
        conversationService.requireAccessible(meId, conversationId);
        return new UnreadCountResponse(messageRepository.countUnread(conversationId, meId));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse totalUnreadCount(Long meId) {
        return new UnreadCountResponse(messageRepository.countUnreadTotal(meId));
    }

    /** Idempotent; marks the other participant's messages as read. */
    @Transactional
    public void markRead(Long meId, Long conversationId) {
        conversationService.requireAccessible(meId, conversationId);
        messageRepository.markRead(conversationId, meId, Instant.now());
    }

    private MessageResponse toResponse(Message m) {
        return new MessageResponse(m.getId(), m.getConversation().getId(), userMapper.toSummary(m.getSender()),
                m.getContent(), m.getCreatedAt());
    }
}
