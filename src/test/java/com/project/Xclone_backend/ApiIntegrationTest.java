package com.project.Xclone_backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    S3Client s3Client;

    @MockitoBean
    S3Presigner s3Presigner;

    record Account(long id, String username, String accessToken, String refreshToken) {
    }

    @Test
    void registerLoginRefreshAndLogout() throws Exception {
        Account a = register();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + a.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(a.username()));

        mvc.perform(json(post("/api/auth/login"),
                        "{\"usernameOrEmail\":\"" + a.username().toUpperCase() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/auth/login"),
                        "{\"usernameOrEmail\":\"" + a.username() + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        String refreshed = mvc.perform(json(post("/api/auth/refresh"), refreshBody(a.refreshToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(refreshed, "$.refreshToken");

        // The rotated-out token is dead, and reusing it revokes the whole token family.
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(a.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(newRefresh)))
                .andExpect(status().isUnauthorized());

        Account b = register();
        mvc.perform(json(post("/api/auth/logout"), refreshBody(b.refreshToken())))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(b.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateAndInvalidRegistrationsAreRejected() throws Exception {
        Account a = register();
        mvc.perform(json(post("/api/auth/register"), registerBody(a.username(), "other-" + a.username())))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/auth/register"),
                        "{\"username\":\"a!\",\"email\":\"bad\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void writesRequireAuthentication() throws Exception {
        mvc.perform(json(post("/api/posts"), "{\"content\":\"hi\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/timeline")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void postsRepliesLikesAndDeletion() throws Exception {
        Account alice = register();
        Account bob = register();

        long postId = createPost(alice, "{\"content\":\"hello world\"}");
        mvc.perform(json(auth(post("/api/posts"), alice), "{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        long replyId = createPost(bob, "{\"content\":\"hi alice\",\"replyToId\":" + postId + "}");

        // Liking twice is idempotent.
        mvc.perform(auth(post("/api/posts/" + postId + "/like"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/posts/" + postId + "/like"), bob)).andExpect(status().isNoContent());

        mvc.perform(auth(get("/api/posts/" + postId), bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.replyCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));
        mvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.likedByMe").value(false));

        mvc.perform(get("/api/posts/" + postId + "/replies"))
                .andExpect(jsonPath("$.items[0].id").value(replyId))
                .andExpect(jsonPath("$.items[0].replyToId").value(postId));
        mvc.perform(get("/api/posts/" + postId + "/likes"))
                .andExpect(jsonPath("$.items[0].username").value(bob.username()));
        mvc.perform(get("/api/users/" + bob.username() + "/likes"))
                .andExpect(jsonPath("$.items[0].id").value(postId));
        mvc.perform(get("/api/users/" + bob.username() + "/replies"))
                .andExpect(jsonPath("$.items[0].id").value(replyId));

        mvc.perform(auth(delete("/api/posts/" + postId + "/like"), bob)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.likeCount").value(0));

        // Only the author may delete; deleting a reply decrements the parent's reply count.
        mvc.perform(auth(delete("/api/posts/" + postId), bob)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/posts/" + replyId), bob)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.replyCount").value(0));
        mvc.perform(auth(delete("/api/posts/" + postId), alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isNotFound());
    }

    @Test
    void followAndTimelinePaging() throws Exception {
        Account alice = register();
        Account bob = register();
        Account carol = register();

        long a1 = createPost(alice, "{\"content\":\"a1\"}");
        long c1 = createPost(carol, "{\"content\":\"c1 - not followed\"}");
        long a2 = createPost(alice, "{\"content\":\"a2\"}");
        long b1 = createPost(bob, "{\"content\":\"b1\"}");

        mvc.perform(auth(post("/api/users/" + alice.username() + "/follow"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/users/" + alice.username() + "/follow"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/users/" + bob.username() + "/follow"), bob)).andExpect(status().isBadRequest());

        mvc.perform(auth(get("/api/users/" + alice.username()), bob))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followedByMe").value(true));
        mvc.perform(get("/api/users/" + alice.username() + "/followers"))
                .andExpect(jsonPath("$.items[*].username", contains(bob.username())));

        String page1 = mvc.perform(auth(get("/api/timeline?limit=2"), bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) b1, (int) a2)))
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(page1, "$.nextCursor");

        mvc.perform(auth(get("/api/timeline?limit=2&cursor=" + cursor), bob))
                .andExpect(jsonPath("$.items[*].id", contains((int) a1)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));

        mvc.perform(auth(delete("/api/users/" + alice.username() + "/follow"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/timeline"), bob))
                .andExpect(jsonPath("$.items[*].id", contains((int) b1)));

        // Carol's post is visible on her profile but never reached Bob's timeline.
        mvc.perform(get("/api/users/" + carol.username() + "/posts"))
                .andExpect(jsonPath("$.items[*].id", contains((int) c1)));
    }

    @Test
    void postWithUploadedMedia() throws Exception {
        Account alice = register();
        Account bob = register();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/png").contentLength(1024L).build());

        String key = "users/" + alice.id() + "/" + UUID.randomUUID() + ".png";
        mvc.perform(json(auth(post("/api/posts"), alice), "{\"mediaKeys\":[\"" + key + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaUrls", hasSize(1)))
                .andExpect(jsonPath("$.mediaUrls[0]").value("https://media.test/" + key));

        // Bob cannot attach Alice's upload.
        mvc.perform(json(auth(post("/api/posts"), bob), "{\"mediaKeys\":[\"" + key + "\"]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(json(auth(patch("/api/users/me"), alice),
                        "{\"avatarKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://media.test/" + key));
    }

    // --- helpers ---

    private Account register() throws Exception {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String body = mvc.perform(json(post("/api/auth/register"), registerBody(username, username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.user.id");
        return new Account(id.longValue(), username, JsonPath.read(body, "$.accessToken"),
                JsonPath.read(body, "$.refreshToken"));
    }

    private long createPost(Account author, String body) throws Exception {
        String res = mvc.perform(json(auth(post("/api/posts"), author), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private static String registerBody(String username, String emailLocal) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + emailLocal + "@example.com\","
                + "\"password\":\"password123\",\"displayName\":\"Test User\"}";
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, Account account) {
        return req.header("Authorization", "Bearer " + account.accessToken());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
