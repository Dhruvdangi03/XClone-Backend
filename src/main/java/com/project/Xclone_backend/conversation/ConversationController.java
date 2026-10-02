package com.project.Xclone_backend.conversation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.conversation.ConversationDtos.ConversationResponse;
import com.project.Xclone_backend.conversation.ConversationDtos.CreateConversationRequest;
import com.project.Xclone_backend.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 201 when the conversation was created, 200 when it already existed. */
    @PostMapping
    public ResponseEntity<ConversationResponse> getOrCreate(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody CreateConversationRequest req) {
        ConversationService.Result result = conversationService.getOrCreate(me.id(), req.username());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.conversation());
    }

    @GetMapping
    public CursorPage<ConversationResponse> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return conversationService.list(me.id(), cursor, limit);
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return conversationService.get(me.id(), id);
    }
}
