package com.project.Xclone_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.project.Xclone_backend.security.JwtService;
import com.project.Xclone_backend.user.User;
import com.project.Xclone_backend.user.UserRepository;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Drives the real STOMP endpoint over a WebSocket against the embedded server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketMessagingTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SimpUserRegistry userRegistry;

    @MockitoBean
    S3Client s3Client;

    @MockitoBean
    S3Presigner s3Presigner;

    private final List<WebSocketStompClient> clients = new java.util.ArrayList<>();

    @AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
    }

    // --- authentication ---

    @Test
    void connectRequiresAValidActiveUserToken() throws Exception {
        Account alice = newUser();
        Account deactivated = newUser();
        jdbc.update("update users set status = 'DEACTIVATED' where id = ?", deactivated.id());

        assertTrue(connect(alice.token()).connected.get(5, TimeUnit.SECONDS) != null);
        assertRejected(connect(null));
        assertRejected(connect("not-a-jwt"));
        assertRejected(connect(deactivated.token()));
    }

    @Test
    void subscribingOutsideTheUserQueueIsRejected() throws Exception {
        Account alice = newUser();
        Connection c = connect(alice.token());
        c.connected.get(5, TimeUnit.SECONDS);
        c.session.subscribe("/queue/messages", c.handler(new LinkedBlockingQueue<>()));
        assertNotNull(c.errored.get(5, TimeUnit.SECONDS));
    }

    @Test
    void deactivatedUserCannotSendOnAnOpenConnection() throws Exception {
        Account alice = newUser();
        Account bob = newUser();
        long conv = newConversation(alice, bob);
        Connection a = connect(alice.token());
        a.connected.get(5, TimeUnit.SECONDS);

        jdbc.update("update users set status = 'DEACTIVATED' where id = ?", alice.id());
        a.session.send(sendDestination(conv), Map.of("content", "still here?"));

        assertNotNull(a.errored.get(5, TimeUnit.SECONDS));
        assertEquals(0, messageCount(conv));
    }

    // --- sending, persistence, delivery ---

    @Test
    void sentMessageIsSavedAndDeliveredOnlyToTheRecipient() throws Exception {
        Account alice = newUser();
        Account bob = newUser();
        Account carol = newUser();
        long conv = newConversation(alice, bob);
        Connection a = connect(alice.token());
        Connection b = connect(bob.token());
        Connection c = connect(carol.token());
        BlockingQueue<Map<String, Object>> aliceInbox = a.subscribe("/user/queue/messages", userRegistry, alice.id());
        BlockingQueue<Map<String, Object>> aliceSent = a.subscribe("/user/queue/sent", userRegistry, alice.id());
        BlockingQueue<Map<String, Object>> bobInbox = b.subscribe("/user/queue/messages", userRegistry, bob.id());
        BlockingQueue<Map<String, Object>> carolInbox = c.subscribe("/user/queue/messages", userRegistry, carol.id());

        a.session.send(sendDestination(conv), Map.of("content", "  hello bob  "));

        Map<String, Object> received = next(bobInbox);
        assertEquals("hello bob", received.get("content"));
        assertEquals(((Number) received.get("conversationId")).longValue(), conv);
        assertEquals(alice.username(), ((Map<?, ?>) received.get("sender")).get("username"));

        Map<String, Object> ack = next(aliceSent);
        assertEquals(received.get("id"), ack.get("id"));

        assertNone(aliceInbox);
        assertNone(carolInbox);
        assertEquals(1, messageCount(conv));
        assertEquals(1, jdbc.queryForObject("select count(*) from messages where conversation_id = ? and read_at is null",
                Integer.class, conv));
        assertTrue(jdbc.queryForObject("select updated_at > created_at from conversations where id = ?",
                Boolean.class, conv));
    }

    @Test
    void messageSentOverRestIsPushedToAConnectedRecipient() throws Exception {
        Account alice = newUser();
        Account bob = newUser();
        long conv = newConversation(alice, bob);
        Connection b = connect(bob.token());
        BlockingQueue<Map<String, Object>> bobInbox = b.subscribe("/user/queue/messages", userRegistry, bob.id());

        HttpResponse<String> res = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/conversations/" + conv + "/messages"))
                .header("Authorization", "Bearer " + alice.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"via rest\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(201, res.statusCode());
        assertEquals("via rest", next(bobInbox).get("content"));
    }

    @Test
    void offlineRecipientDoesNotBreakSending() throws Exception {
        Account alice = newUser();
        Account bob = newUser();
        long conv = newConversation(alice, bob);
        Connection a = connect(alice.token());
        BlockingQueue<Map<String, Object>> aliceSent = a.subscribe("/user/queue/sent", userRegistry, alice.id());
        BlockingQueue<Map<String, Object>> aliceErrors = a.subscribe("/user/queue/errors", userRegistry, alice.id());

        a.session.send(sendDestination(conv), Map.of("content", "are you there"));

        assertEquals("are you there", next(aliceSent).get("content"));
        assertNone(aliceErrors);
        assertEquals(1, messageCount(conv));
    }

    // --- authorization and validation ---

    @Test
    void invalidSendsAreRejectedToTheCallerOnly() throws Exception {
        Account alice = newUser();
        Account bob = newUser();
        Account carol = newUser();
        Account dave = newUser();
        long conv = newConversation(alice, bob);
        long blockedConv = newConversation(alice, dave);
        jdbc.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", dave.id(),
                alice.id());

        Connection a = connect(alice.token());
        Connection b = connect(bob.token());
        Connection c = connect(carol.token());
        BlockingQueue<Map<String, Object>> aliceErrors = a.subscribe("/user/queue/errors", userRegistry, alice.id());
        BlockingQueue<Map<String, Object>> bobInbox = b.subscribe("/user/queue/messages", userRegistry, bob.id());
        BlockingQueue<Map<String, Object>> carolErrors = c.subscribe("/user/queue/errors", userRegistry, carol.id());

        // Not a participant.
        c.session.send(sendDestination(conv), Map.of("content", "intruder"));
        assertEquals(404, ((Number) next(carolErrors).get("status")).intValue());

        // Unknown conversation.
        a.session.send(sendDestination(999_999_999L), Map.of("content", "nowhere"));
        assertEquals(404, ((Number) next(aliceErrors).get("status")).intValue());

        // Blank, missing and over-long content.
        a.session.send(sendDestination(conv), Map.of("content", "   "));
        assertEquals(400, ((Number) next(aliceErrors).get("status")).intValue());
        a.session.send(sendDestination(conv), Map.of());
        assertEquals(400, ((Number) next(aliceErrors).get("status")).intValue());
        a.session.send(sendDestination(conv), Map.of("content", "a".repeat(2001)));
        assertEquals(400, ((Number) next(aliceErrors).get("status")).intValue());

        // Blocked pair.
        a.session.send(sendDestination(blockedConv), Map.of("content", "blocked"));
        assertEquals(403, ((Number) next(aliceErrors).get("status")).intValue());

        assertNone(bobInbox);
        assertEquals(0, messageCount(conv));
        assertEquals(0, messageCount(blockedConv));

        // The connection survives errors and still works afterwards.
        BlockingQueue<Map<String, Object>> aliceSent = a.subscribe("/user/queue/sent", userRegistry, alice.id());
        a.session.send(sendDestination(conv), Map.of("content", "fine"));
        assertEquals("fine", next(aliceSent).get("content"));
    }

    // --- helpers ---

    record Account(long id, String username, String token) {
    }

    private Account newUser() {
        String username = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("not-used");
        user.setDisplayName("Test User");
        userRepository.save(user);
        return new Account(user.getId(), username, jwtService.createAccessToken(user.getId(), username));
    }

    private long newConversation(Account a, Account b) {
        return jdbc.queryForObject("""
                insert into conversations (user_one_id, user_two_id, created_at, updated_at)
                values (?, ?, now() - interval '1 minute', now() - interval '1 minute') returning id
                """, Long.class, Math.min(a.id(), b.id()), Math.max(a.id(), b.id()));
    }

    private int messageCount(long conversationId) {
        return jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class,
                conversationId);
    }

    private static String sendDestination(long conversationId) {
        return "/app/conversations/" + conversationId + "/messages";
    }

    private static Map<String, Object> next(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        Map<String, Object> m = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(m, "expected a message but none arrived");
        return m;
    }

    private static void assertNone(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        assertNull(queue.poll(700, TimeUnit.MILLISECONDS), "unexpected message");
    }

    private static void assertRejected(Connection c) throws Exception {
        assertNotNull(c.errored.get(5, TimeUnit.SECONDS));
        assertFalse(c.connected.isDone());
    }

    private Connection connect(String token) {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        clients.add(client);
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        Connection c = new Connection();
        client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers, c);
        return c;
    }

    /** One STOMP session; {@code connected} completes on CONNECTED, {@code errored} on an ERROR frame or drop. */
    static class Connection extends StompSessionHandlerAdapter {

        final CompletableFuture<StompSession> connected = new CompletableFuture<>();
        final CompletableFuture<Object> errored = new CompletableFuture<>();
        volatile StompSession session;

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            this.session = session;
            connected.complete(session);
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errored.complete(headers);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errored.complete(exception);
        }

        @Override
        public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                StompHeaders headers, byte[] payload, Throwable exception) {
            errored.complete(exception);
        }

        StompFrameHandler handler(BlockingQueue<Map<String, Object>> queue) {
            return new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((Map<String, Object>) payload);
                }
            };
        }

        /** Subscribes, then returns once the server has registered it so later sends cannot race it. */
        BlockingQueue<Map<String, Object>> subscribe(String destination, SimpUserRegistry registry, long userId)
                throws Exception {
            session = connected.get(5, TimeUnit.SECONDS);
            BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
            int before = subscriptionCount(registry, userId);
            session.subscribe(destination, handler(queue));
            long deadline = System.currentTimeMillis() + 5000;
            while (subscriptionCount(registry, userId) <= before) {
                assertTrue(System.currentTimeMillis() < deadline, "subscription not registered");
                Thread.sleep(20);
            }
            return queue;
        }

        private static int subscriptionCount(SimpUserRegistry registry, long userId) {
            var user = registry.getUser(String.valueOf(userId));
            return user == null ? 0 : user.getSessions().stream().mapToInt(x -> x.getSubscriptions().size()).sum();
        }
    }
}
