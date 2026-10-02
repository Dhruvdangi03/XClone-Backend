# X clone backend

A minimal X (Twitter) clone REST API built with Spring Boot 4, PostgreSQL, and Cloudflare R2 for images.

Features: JWT auth (access + rotating refresh tokens), profiles, posts with up to 4 images, replies, likes, follows, blocking, a home timeline, and user search.

## Running locally

Requirements: Java 17+ and a local PostgreSQL.

```bash
createdb -U postgres xclone          # once
createdb -U postgres xclone_test     # once, used by the tests
./mvnw spring-boot:run
```

Tables are created automatically (`ddl-auto: update`). Configuration comes from environment variables; see `.env.example`. The defaults connect to `localhost:5432/xclone` as `postgres`/`postgres`. **Set `JWT_SECRET` in any real environment.**

R2 is optional for local development. Without `R2_ACCOUNT_ID`/`R2_ACCESS_KEY`/`R2_SECRET_KEY`, everything works except media endpoints, which return `503`.

Run the tests with `./mvnw test`. They need the `xclone_test` database, and R2 is mocked.

## Cloudflare R2 setup

1. Create a bucket and an R2 API token with Object Read & Write access.
2. Enable public access (the r2.dev subdomain or a custom domain) and set `R2_PUBLIC_BASE_URL` to it.
3. Add a CORS policy on the bucket so browsers can upload directly:

```json
[{ "AllowedOrigins": ["http://localhost:5173"], "AllowedMethods": ["PUT"], "AllowedHeaders": ["content-type", "content-length"], "MaxAgeSeconds": 3600 }]
```

### Image upload flow

1. `POST /api/media/upload-url` `{ "contentType": "image/png", "contentLength": 12345 }` returns `{ key, uploadUrl, headers, publicUrl, expiresAt }`.
2. `PUT` the file bytes to `uploadUrl` with the returned `headers` (Content-Type must match).
3. Use `key` in `mediaKeys` when creating a post, or as `avatarKey`/`bannerKey` in `PATCH /api/users/me`.

Allowed types are jpeg, png, webp and gif, up to 5 MB by default. The server only accepts keys under the caller's own `users/{id}/` prefix that actually exist in the bucket.

## API

All endpoints are under `/api`. Send `Authorization: Bearer <accessToken>` for authenticated calls. Errors use RFC 9457 problem JSON.

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/register` | – | `{username, email, password, displayName}` |
| POST | `/auth/login` | – | `{usernameOrEmail, password}` |
| POST | `/auth/refresh` | – | `{refreshToken}`; rotates the token, and reusing an old one revokes all sessions |
| POST | `/auth/logout` | – | `{refreshToken}` |
| GET / PATCH | `/users/me` | ✓ | PATCH `{displayName?, bio?, avatarKey?, bannerKey?}`; `""` clears |
| GET | `/users/search?q=` | – | prefix match on username / display name |
| GET | `/users/me/blocks` | ✓ | users you blocked, paged |
| GET | `/users/{username}` | optional | profile + counts + `followedByMe` + `blockedByMe` |
| POST / DELETE | `/users/{username}/follow` | ✓ | idempotent |
| POST / DELETE | `/users/{username}/block` | ✓ | idempotent; removes follows both ways. Blocked pairs can't follow, like or reply to each other or see each other's posts (403), and are hidden from each other's reply and follower lists |
| GET | `/users/{username}/followers`, `/following` | – | paged |
| GET | `/users/{username}/posts`, `/replies`, `/likes` | optional | paged |
| POST | `/posts` | ✓ | `{content?, mediaKeys?, replyToId?}` |
| GET / DELETE | `/posts/{id}` | optional / ✓ | delete is author-only (soft delete) |
| GET | `/posts/{id}/replies` | optional | paged, oldest first |
| POST / DELETE | `/posts/{id}/like` | ✓ | idempotent |
| GET | `/posts/{id}/likes` | – | users who liked, paged |
| GET | `/notifications` | ✓ | follow / like / reply / mention notifications, newest first, paged; hides blocked or inactive actors |
| GET | `/notifications/unread-count` | ✓ | |
| POST | `/notifications/read`, `/notifications/{id}/read` | ✓ | mark all / one as read |
| DELETE | `/notifications/{id}` | ✓ | |
| GET | `/timeline` | ✓ | your posts + people you follow, newest first |
| POST | `/media/upload-url` | ✓ | see above |

**Pagination.** Paged endpoints accept `?cursor=&limit=` (default 20, max 50) and return `{ items, nextCursor }`. Pass `nextCursor` back to get the next page; `null` means there are no more pages.
