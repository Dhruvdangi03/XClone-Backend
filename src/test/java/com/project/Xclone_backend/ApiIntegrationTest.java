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

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.project.Xclone_backend.hashtag.HashtagRepository;
import com.project.Xclone_backend.report.PostReportRepository;
import com.project.Xclone_backend.report.UserReportRepository;

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

    @Autowired
    HashtagRepository hashtagRepository;

    @Autowired
    UserReportRepository userReportRepository;

    @Autowired
    PostReportRepository postReportRepository;

    @Autowired
    JdbcTemplate jdbc;

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
    void editingPosts() throws Exception {
        Account alice = register();
        Account bob = register();
        long postId = createPost(alice, "{\"content\":\"original\"}");
        mvc.perform(auth(post("/api/posts/" + postId + "/like"), bob)).andExpect(status().isNoContent());
        String before = mvc.perform(get("/api/posts/" + postId)).andReturn().getResponse().getContentAsString();

        mvc.perform(json(patch("/api/posts/" + postId), "{\"content\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(auth(patch("/api/posts/" + postId), bob), "{\"content\":\"hijack\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(auth(patch("/api/posts/999999999"), alice), "{\"content\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(auth(patch("/api/posts/" + postId), alice), "{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(auth(patch("/api/posts/" + postId), alice), "{\"content\":\"" + "a".repeat(281) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.content").value("original"));

        mvc.perform(json(auth(patch("/api/posts/" + postId), alice), "{\"content\":\"  edited  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.content").value("edited"))
                .andExpect(jsonPath("$.author.username").value(alice.username()))
                .andExpect(jsonPath("$.likeCount").value(1));

        String after = mvc.perform(get("/api/posts/" + postId)).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(JsonPath.read(before, "$.createdAt").toString(),
                JsonPath.read(after, "$.createdAt").toString());
        org.junit.jupiter.api.Assertions.assertEquals("edited", JsonPath.read(after, "$.content"));

        mvc.perform(auth(delete("/api/posts/" + postId), alice)).andExpect(status().isNoContent());
        mvc.perform(json(auth(patch("/api/posts/" + postId), alice), "{\"content\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void hashtags() throws Exception {
        Account alice = register();
        Account bob = register();
        // Unique per run so tags don't collide with posts from other tests.
        String t = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        long postId = createPost(alice, "{\"content\":\"Learning #Java" + t + " #Spring" + t + " #JAVA" + t + "\"}");
        long otherId = createPost(bob, "{\"content\":\"#java" + t + " too\"}");

        // Case-insensitive lookup; a repeated tag links the post only once.
        mvc.perform(get("/api/hashtags/JAVA" + t + "/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) otherId, (int) postId)));
        mvc.perform(get("/api/hashtags/{name}/posts", "#Spring" + t))
                .andExpect(jsonPath("$.items[*].id", contains((int) postId)));
        org.assertj.core.api.Assertions.assertThat(
                hashtagRepository.findByNameIn(List.of("java" + t, "spring" + t))).hasSize(2);

        // Keyset paging.
        mvc.perform(get("/api/hashtags/java" + t + "/posts").param("limit", "1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").value(otherId));

        // Editing re-syncs tags: adding keeps old ones, replacing drops them.
        mvc.perform(json(auth(patch("/api/posts/" + postId), alice),
                        "{\"content\":\"Learning #Java" + t + " and #Boot" + t + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/hashtags/java" + t + "/posts"))
                .andExpect(jsonPath("$.items[*].id", contains((int) otherId, (int) postId)));
        mvc.perform(get("/api/hashtags/boot" + t + "/posts"))
                .andExpect(jsonPath("$.items[*].id", contains((int) postId)));
        mvc.perform(get("/api/hashtags/spring" + t + "/posts")).andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(json(auth(patch("/api/posts/" + postId), alice), "{\"content\":\"Learning #React" + t + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/hashtags/java" + t + "/posts"))
                .andExpect(jsonPath("$.items[*].id", contains((int) otherId)));
        mvc.perform(get("/api/hashtags/boot" + t + "/posts")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/hashtags/react" + t + "/posts"))
                .andExpect(jsonPath("$.items[*].id", contains((int) postId)))
                .andExpect(jsonPath("$.items[0].author.username").value(alice.username()));

        // Deleted posts drop out; unknown tags give an empty page.
        mvc.perform(auth(delete("/api/posts/" + postId), alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/hashtags/react" + t + "/posts")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/hashtags/nothing" + t + "/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void reportUser() throws Exception {
        Account alice = register();
        Account bob = register();
        Account carol = register();
        String url = "/api/users/" + bob.username() + "/report";

        mvc.perform(json(post(url), "{\"reason\":\"SPAM\"}")).andExpect(status().isUnauthorized());
        mvc.perform(json(auth(post(url), alice), "{}")).andExpect(status().isBadRequest());
        mvc.perform(json(auth(post(url), alice), "{\"reason\":\"FOO\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(auth(post("/api/users/nobody_" + UUID.randomUUID().toString().substring(0, 8) + "/report"),
                        alice), "{\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(auth(post("/api/users/" + alice.username() + "/report"), alice), "{\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(json(auth(post(url), alice), "{\"reason\":\"HARASSMENT\"}")).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(
                userReportRepository.existsByReporterIdAndReportedUserId(alice.id(), bob.id())).isTrue();
        mvc.perform(json(auth(post(url), alice), "{\"reason\":\"SPAM\"}")).andExpect(status().isConflict());
        mvc.perform(json(auth(post(url), carol), "{\"reason\":\"OTHER\"}")).andExpect(status().isNoContent());

        // Reporting does not affect the reported account.
        mvc.perform(get("/api/users/" + bob.username())).andExpect(status().isOk());
        mvc.perform(auth(get("/api/users/me"), bob)).andExpect(status().isOk());
    }

    @Test
    void reportPost() throws Exception {
        Account alice = register();
        Account bob = register();
        Account carol = register();
        long postId = createPost(alice, "{\"content\":\"reportable\"}");
        String url = "/api/posts/" + postId + "/report";

        mvc.perform(json(post(url), "{\"reason\":\"SPAM\"}")).andExpect(status().isUnauthorized());
        mvc.perform(json(auth(post(url), bob), "{}")).andExpect(status().isBadRequest());
        mvc.perform(json(auth(post(url), bob), "{\"reason\":\"spam\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(auth(post("/api/posts/999999999/report"), bob), "{\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(auth(post(url), alice), "{\"reason\":\"SPAM\"}")).andExpect(status().isBadRequest());

        mvc.perform(json(auth(post(url), bob), "{\"reason\":\"MISINFORMATION\"}")).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(
                postReportRepository.existsByReporterIdAndPostId(bob.id(), postId)).isTrue();
        mvc.perform(json(auth(post(url), bob), "{\"reason\":\"SPAM\"}")).andExpect(status().isConflict());
        mvc.perform(json(auth(post(url), carol), "{\"reason\":\"OTHER\"}")).andExpect(status().isNoContent());

        // Reporting does not hide or change the post.
        mvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("reportable"));

        // Deleted posts can no longer be reported.
        mvc.perform(auth(delete("/api/posts/" + postId), alice)).andExpect(status().isNoContent());
        mvc.perform(json(auth(post(url), register()), "{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
    }

    @Test
    void changeUsernameAndEmail() throws Exception {
        Account alice = register();
        Account bob = register();
        String newName = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        mvc.perform(json(patch("/api/users/me/username"), "{\"username\":\"" + newName + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(auth(patch("/api/users/me/username"), alice), "{\"username\":\"a!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(auth(patch("/api/users/me/username"), alice), "{\"username\":\"" + bob.username() + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(auth(patch("/api/users/me/username"), alice),
                        "{\"username\":\"" + alice.username().toUpperCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(alice.username()));

        mvc.perform(json(auth(patch("/api/users/me/username"), alice), "{\"username\":\"" + newName.toUpperCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.id()))
                .andExpect(jsonPath("$.username").value(newName))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users/" + alice.username())).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/" + newName)).andExpect(jsonPath("$.id").value(alice.id()));
        mvc.perform(auth(get("/api/users/me"), alice)).andExpect(jsonPath("$.username").value(newName));
        mvc.perform(json(post("/api/auth/login"), "{\"usernameOrEmail\":\"" + newName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(auth(patch("/api/users/me/username"), bob), "{\"username\":\"" + newName + "\"}"))
                .andExpect(status().isConflict());

        String newEmail = newName + "@Example.org";
        mvc.perform(json(auth(patch("/api/users/me/email"), alice), "{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(auth(patch("/api/users/me/email"), alice), "{\"email\":\"" + bob.username() + "@example.com\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(auth(patch("/api/users/me/email"), alice), "{\"email\":\"" + newEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail.toLowerCase()))
                .andExpect(jsonPath("$.username").value(newName));
        mvc.perform(json(post("/api/auth/login"), "{\"usernameOrEmail\":\"" + newEmail + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword() throws Exception {
        Account a = register();
        String url = "/api/users/me/password";

        mvc.perform(json(patch(url), "{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(auth(patch(url), a), "{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(auth(patch(url), a), "{\"currentPassword\":\"password123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(auth(patch(url), a), "{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        mvc.perform(json(post("/api/auth/login"), "{\"usernameOrEmail\":\"" + a.username() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/login"), "{\"usernameOrEmail\":\"" + a.username() + "\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isOk());
        // Existing sessions are signed out.
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(a.refreshToken()))).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateAccount() throws Exception {
        Account a = register();
        long postId = createPost(a, "{\"content\":\"still here\"}");

        mvc.perform(post("/api/users/me/deactivate")).andExpect(status().isUnauthorized());
        mvc.perform(auth(post("/api/users/me/deactivate"), a)).andExpect(status().isNoContent());

        // The old access token stops working immediately, and no new session can be started.
        mvc.perform(auth(get("/api/users/me"), a)).andExpect(status().isUnauthorized());
        mvc.perform(json(auth(post("/api/posts"), a), "{\"content\":\"nope\"}")).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(a.refreshToken()))).andExpect(status().isUnauthorized());

        // A wrong password does not reactivate: the old token would work again if the status had flipped.
        mvc.perform(json(post("/api/auth/login"), "{\"usernameOrEmail\":\"" + a.username() + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/users/me"), a)).andExpect(status().isUnauthorized());

        // Nothing is deleted.
        mvc.perform(get("/api/users/" + a.username())).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("still here"));

        // Logging in with the correct password (by email here) reactivates and issues a normal token pair.
        String body = mvc.perform(json(post("/api/auth/login"),
                        "{\"usernameOrEmail\":\"" + a.username() + "@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(a.id()))
                .andReturn().getResponse().getContentAsString();
        Account back = new Account(a.id(), a.username(), JsonPath.read(body, "$.accessToken"),
                JsonPath.read(body, "$.refreshToken"));

        mvc.perform(auth(get("/api/users/me"), back)).andExpect(status().isOk());
        createPost(back, "{\"content\":\"back again\"}");
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(back.refreshToken()))).andExpect(status().isOk());
        // Sessions revoked at deactivation stay revoked.
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(a.refreshToken()))).andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount() throws Exception {
        Account alice = register();
        Account bob = register();
        Account carol = register();
        String tag = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        long bobPost = createPost(bob, "{\"content\":\"bob here\"}");
        long alicePost = createPost(alice, "{\"content\":\"alice #" + tag + "\"}");
        createPost(alice, "{\"content\":\"reply from alice\",\"replyToId\":" + bobPost + "}");
        long bobReply = createPost(bob, "{\"content\":\"reply to alice\",\"replyToId\":" + alicePost + "}");
        mvc.perform(auth(post("/api/posts/" + bobPost + "/like"), alice)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/users/" + bob.username() + "/follow"), alice)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/users/" + alice.username() + "/follow"), carol)).andExpect(status().isNoContent());
        mvc.perform(json(auth(post("/api/users/" + alice.username() + "/report"), bob), "{\"reason\":\"SPAM\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + bobPost))
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.replyCount").value(1));

        mvc.perform(json(delete("/api/users/me"), "{\"password\":\"password123\"}")).andExpect(status().isUnauthorized());
        mvc.perform(json(auth(delete("/api/users/me"), alice), "{}")).andExpect(status().isBadRequest());
        mvc.perform(json(auth(delete("/api/users/me"), alice), "{\"password\":\"wrong-pass\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/" + alice.username())).andExpect(status().isOk());

        mvc.perform(json(auth(delete("/api/users/me"), alice), "{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());

        // The account is gone and cannot be used.
        mvc.perform(get("/api/users/" + alice.username())).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/search").param("q", alice.username())).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(auth(get("/api/users/me"), alice)).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/refresh"), refreshBody(alice.refreshToken()))).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/login"), "{\"usernameOrEmail\":\"" + alice.username() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());

        // Alice's content and activity are removed, and counts on other users' posts are corrected.
        mvc.perform(get("/api/posts/" + alicePost)).andExpect(status().isNotFound());
        mvc.perform(get("/api/hashtags/" + tag + "/posts")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/posts/" + bobPost))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.replyCount").value(0));
        mvc.perform(get("/api/users/" + bob.username())).andExpect(jsonPath("$.followerCount").value(0));
        mvc.perform(get("/api/users/" + carol.username())).andExpect(jsonPath("$.followingCount").value(0));

        // Other users' content and reports stay intact.
        mvc.perform(get("/api/posts/" + bobReply))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyToId").value(alicePost));
        org.assertj.core.api.Assertions.assertThat(
                userReportRepository.existsByReporterIdAndReportedUserId(bob.id(), alice.id())).isTrue();

        // The username and email are free again.
        mvc.perform(json(post("/api/auth/register"), registerBody(alice.username(), alice.username())))
                .andExpect(status().isCreated());
    }

    @Test
    void reposts() throws Exception {
        Account alice = register();
        Account bob = register();
        Account carol = register();
        long postId = createPost(alice, "{\"content\":\"worth sharing\"}");
        String url = "/api/posts/" + postId + "/repost";
        mvc.perform(auth(post("/api/users/" + bob.username() + "/follow"), carol)).andExpect(status().isNoContent());

        // Auth, existence and ownership rules.
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(auth(post("/api/posts/999999999/repost"), bob)).andExpect(status().isNotFound());
        mvc.perform(auth(post(url), alice)).andExpect(status().isBadRequest());

        // Reposting twice keeps a single repost.
        mvc.perform(auth(post(url), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(post(url), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/posts/" + postId), bob))
                .andExpect(jsonPath("$.repostCount").value(1))
                .andExpect(jsonPath("$.repostedByMe").value(true))
                .andExpect(jsonPath("$.repostedBy").value(nullValue()));
        mvc.perform(auth(get("/api/posts/" + postId), carol)).andExpect(jsonPath("$.repostedByMe").value(false));

        // The repost appears in followers' timelines and the reposter's profile as the original post.
        String timeline = mvc.perform(auth(get("/api/timeline"), carol))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(postId))
                .andExpect(jsonPath("$.items[0].author.username").value(alice.username()))
                .andExpect(jsonPath("$.items[0].content").value("worth sharing"))
                .andExpect(jsonPath("$.items[0].repostedBy.username").value(bob.username()))
                .andExpect(jsonPath("$.items[0].repostCount").value(1))
                .andExpect(jsonPath("$.items[0].repostedByMe").value(false))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat((Object) JsonPath.read(timeline, "$.nextCursor")).isNull();
        mvc.perform(auth(get("/api/users/" + bob.username() + "/posts"), bob))
                .andExpect(jsonPath("$.items[0].id").value(postId))
                .andExpect(jsonPath("$.items[0].repostedBy.username").value(bob.username()))
                .andExpect(jsonPath("$.items[0].repostedByMe").value(true));
        // The original author's profile is unchanged: one post, not reposted.
        mvc.perform(get("/api/users/" + alice.username() + "/posts"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].repostedBy").value(nullValue()));

        // Undo is idempotent and removes the repost everywhere.
        mvc.perform(auth(delete(url), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(delete(url), bob)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.repostCount").value(0));
        mvc.perform(auth(get("/api/timeline"), carol)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/users/" + bob.username() + "/posts")).andExpect(jsonPath("$.items", hasSize(0)));

        // Reposting again works after an undo.
        mvc.perform(auth(post(url), bob)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.repostCount").value(1));

        // Deleting the original hides existing reposts, and it can no longer be reposted.
        mvc.perform(auth(delete("/api/posts/" + postId), alice)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/timeline"), carol)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(auth(post(url), carol)).andExpect(status().isNotFound());
    }

    @Test
    void repostAccessRules() throws Exception {
        Account alice = register();
        Account bob = register();
        Account carol = register();
        long postId = createPost(alice, "{\"content\":\"original\"}");
        mvc.perform(auth(post("/api/posts/" + postId + "/repost"), bob)).andExpect(status().isNoContent());

        // A repost row's own id is not a post that can be fetched or reposted.
        Long repostRowId = jdbc.queryForObject("select id from posts where author_id = ? and repost_of_id = ?",
                Long.class, bob.id(), postId);
        mvc.perform(get("/api/posts/" + repostRowId)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/posts/" + repostRowId + "/repost"), carol)).andExpect(status().isNotFound());

        // A deactivated user cannot repost with an old token.
        mvc.perform(auth(post("/api/users/me/deactivate"), carol)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/posts/" + postId + "/repost"), carol)).andExpect(status().isUnauthorized());

        // When the reposter deletes their account, their repost and its count go away.
        mvc.perform(json(auth(delete("/api/users/me"), bob), "{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.repostCount").value(0));

        // Posts of a deleted account cannot be reposted.
        Account dave = register();
        mvc.perform(json(auth(delete("/api/users/me"), alice), "{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/posts/" + postId + "/repost"), dave)).andExpect(status().isNotFound());
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
    void blockingUsers() throws Exception {
        Account alice = register();
        Account bob = register();
        Account carol = register();

        long a1 = createPost(alice, "{\"content\":\"a1\"}");
        long b1 = createPost(bob, "{\"content\":\"b1\"}");
        long carolReplyId = createPost(carol, "{\"content\":\"carol reply\",\"replyToId\":" + a1 + "}");
        long bobReplyId = createPost(bob, "{\"content\":\"bob reply\",\"replyToId\":" + a1 + "}");
        mvc.perform(auth(post("/api/users/" + alice.username() + "/follow"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/users/" + bob.username() + "/follow"), alice)).andExpect(status().isNoContent());

        mvc.perform(auth(post("/api/users/" + bob.username() + "/block"), alice)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/users/" + bob.username() + "/block"), alice)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/users/" + alice.username() + "/block"), alice)).andExpect(status().isBadRequest());

        // Blocking removed the follows both ways and shows on the profile.
        mvc.perform(auth(get("/api/users/" + bob.username()), alice))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.blockedByMe").value(true));
        mvc.perform(auth(get("/api/users/me/blocks"), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username", contains(bob.username())));
        mvc.perform(get("/api/users/me/blocks")).andExpect(status().isUnauthorized());

        // No interactions in either direction.
        mvc.perform(auth(post("/api/users/" + alice.username() + "/follow"), bob)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/users/" + bob.username() + "/follow"), alice)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/posts/" + a1 + "/like"), bob)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/posts/" + b1 + "/like"), alice)).andExpect(status().isForbidden());
        mvc.perform(json(auth(post("/api/posts"), bob), "{\"content\":\"hi\",\"replyToId\":" + a1 + "}"))
                .andExpect(status().isForbidden());

        // Content is hidden from both sides, but not from others.
        mvc.perform(auth(get("/api/posts/" + a1), bob)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/users/" + bob.username() + "/posts"), alice)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/posts/" + a1 + "/replies"), alice))
                .andExpect(jsonPath("$.items[*].id", contains((int) carolReplyId)));
        mvc.perform(auth(get("/api/posts/" + a1 + "/replies"), carol))
                .andExpect(jsonPath("$.items[*].id", contains((int) carolReplyId, (int) bobReplyId)));
        mvc.perform(auth(post("/api/users/" + carol.username() + "/follow"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/users/" + carol.username() + "/followers"), alice))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/users/" + carol.username() + "/followers"))
                .andExpect(jsonPath("$.items[*].username", contains(bob.username())));

        mvc.perform(auth(delete("/api/users/" + bob.username() + "/block"), alice)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/users/" + bob.username()), alice))
                .andExpect(jsonPath("$.blockedByMe").value(false));
        mvc.perform(auth(post("/api/posts/" + a1 + "/like"), bob)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/users/me/blocks"), alice)).andExpect(jsonPath("$.items", hasSize(0)));
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
