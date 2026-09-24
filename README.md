# ExamPrep — Online Test-Series Platform (JEE / NEET)

A modular-monolith Spring Boot 3.5 / Java 21 backend with a React 18 frontend for timed mock tests:
server-authoritative timers, Redis-buffered autosave, instant results, ranks and percentiles.

> **Status: product complete (Phases 1–7).** Phase 8 adds architecture and ER diagrams, the full API
> list, an OpenAPI/Postman collection and CI polish.

## Phase 7: exam, results and admin UI

| Route | Screen |
|---|---|
| `/tests/:testId` | Instructions: sections, marking scheme, attempts left, "I agree" gate, start/resume, attempt history |
| `/exam/:attemptId` | NTA-style exam, full-screen without site chrome (details below) |
| `/attempts/:id/result` | Polls while `EVALUATING`. Score, rank (provisional/final), percentile, accuracy, time; outcome pie, section bars, section table, you vs topper vs average, weakest topics first. Shows `AWAITING_PUBLICATION` until a scheduled test closes |
| `/attempts/:id/solutions` | Your answer vs the key, marks awarded, time spent, explanations (KaTeX, images, video link). Filter by section and outcome |
| `/tests/:testId/leaderboard` | Top 100 with medals, your row pinned, refreshed every 30 s |
| `/admin/dashboard` (admin) | KPIs, attempts/revenue per day, top series, users/tests breakdown |
| `/admin/questions` | Cascading exam → subject → chapter → topic filters (URL-synced), type/difficulty/status/text search, archive, **bulk import** (template download, validate-only dry run, error report per row) |
| `/admin/questions/new`, `/:id` | Editor for all 5 types with a **live KaTeX preview**, option images and figures uploaded to MinIO/S3, answer key per type, solution, marks, tags. Type and key lock when the question is in a published test |
| `/admin/tests`, `/:id`, `/:id/stats` | Create (standard patterns build their sections), builder (sections, question picker, auto-fill per section or full paper by difficulty mix, **drag-and-drop or ↑/↓ reorder**, per-question marks, live publish checklist, publish/unpublish/archive/delete), stats (distribution, per-question flags, results, finalize ranks, re-evaluate) |
| `/admin/series`, `/admin/users` (admin) | Series CRUD (price, validity, thumbnail upload, batch restriction, publish lifecycle); users search, create, status, roles |

Teachers see only the question bank and tests. The UI hides links by role, and the API enforces every rule again.

**The exam screen**
- **Timer**: server clock offset, so the device clock does not matter. It turns red in the last 5 minutes and submits automatically at 0.
- **Autosave**: every 12 s, and on each navigation, with at most one request in flight. It backs off while offline and keeps a heartbeat. A `fetch keepalive` save fires when the page is closed.
- **Crash-safe local backup**: pending answers are mirrored to `localStorage` with the highest seq sent per question. After a reload, an entry is replayed unless the server already holds a newer seq for that question.
- **Palette**: NTA colours and shapes, counts, per-section grid. On mobile it opens as a drawer.
- **Answers**: MCQ single and multiple, numerical (strict decimal typing), match-the-columns, and passages. The "attempt any N" limit is checked on the client as well as the server. Rejected answers roll back.
- **Proctoring signals**: tab switches, blur, full-screen exit, copy/paste/right-click (blocked), and online/offline. Events are batched to the server, which may auto-submit after the configured tab-switch limit. A warning banner and an offline indicator are shown.
- **Submit dialog**: a per-section table of the five answer states.

```
frontend/src/
  features/exam/      examStore (zustand) · useAutosave · useAntiCheat · ExamPage · TestInstructionsPage · components/{Palette,QuestionPanel}
  features/results/   ResultPage · SolutionsPage · LeaderboardPage
  features/admin/     AdminLayout · dashboard · questions (+ questionForm, editor) · tests (+ builder, stats) · series · users · components/
  api/                attempts.ts (student exam/results) · admin.ts (all staff endpoints)
  types/              exam.ts · admin.ts
```

**Try it** (backend on :8080 with dev data, frontend via `npm run dev`):
1. Log in as `student@examprep.local` / `Student@123` and open **Test series → JEE Main Free Practice → Sample Mock Test 1**. Agree to the instructions and start. Answer a few questions, reload the tab mid-test (nothing is lost), then submit.
2. The result appears within seconds. Open **Solutions** and **Leaderboard**. The dashboard now lists the result.
3. Log in as `admin@examprep.local` (password from `ADMIN_INITIAL_PASSWORD`, default `Admin@123`) and open **Admin**. Create a question, create a CUSTOM test, add a section and questions, reorder them, and watch the checklist turn green before publishing.
4. `teacher@examprep.local` / `Teacher@123` sees only the question bank and tests.

Tests: `npm test` runs 33 tests. New in this phase: exam store (palette states, seq ordering, in-flight edits, rollback, backup restore rules), question form (per-type validation and request shaping), and the LaTeX parser.


## Phase 6: frontend foundation (`frontend/`)

React 18 · Vite 8 · TypeScript 5.9 · React Router 6 (data router, lazy routes) · TanStack Query 5 ·
Zustand · Tailwind CSS 4 + shadcn/ui (new-york) · react-hook-form + zod · Axios · Recharts · Vitest.

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173 (Vite proxies /api to :8080, same origin, no CORS)
npm test             # vitest + testing-library
npm run build        # typecheck + production bundle in dist/
```
If 5173 is taken: `npx vite --port 5174`. With Docker: `docker compose up -d --build`, then open
**http://localhost:3000** (nginx serves the SPA and proxies `/api` to the backend).

| Area | Details |
|---|---|
| Pages | Landing, test-series list (URL-synced filters, pagination), series detail (enroll or buy), login, register, forgot/reset password, dashboard (KPIs, score-trend chart, weak topics), my series, profile + change password |
| Auth | Session persisted in localStorage and synced across tabs. `RequireAuth` / `GuestOnly` guards with `?next=` return (open-redirect safe) |
| Token refresh | Automatic on `TOKEN_EXPIRED`. **Single-flight per tab plus Web Locks across tabs**, because refresh tokens are single-use and two tabs must never spend the same one. A network failure during refresh **does not** log the user out (important mid-exam). `SESSION_REVOKED` ends the session |
| Errors | Normalised `ApiError`. Server `VALIDATION_FAILED` maps onto form fields. Background refetch and mutation errors become toasts. First-load errors render in place with retry. A render error boundary backs it all up |
| Payments | `usePurchase`: order, then Razorpay Checkout (loaded on demand), then server verification. In MOCK mode a confirmation dialog calls `/payments/{id}/mock-checkout` |
| Code splitting | One chunk per route. The landing page is about 150 kB gzipped, and charts load only with the dashboard |

**Project layout**
```
frontend/src/
  api/          typed endpoint modules + TanStack Query hooks (auth, catalog, series, payments, analytics)
  components/   ui/ (shadcn) · layout/ (Navbar, layouts) · common/ (states, FormField, Pagination) · routing/ (guards)
  features/     public · auth · series · student · errors
  hooks/        useLogout, usePurchase
  lib/          api (axios + refresh), errors, queryClient, format, razorpay
  store/        auth (zustand persist)
```

## Phase 5: evaluation, ranking, results, analytics

```
 submit (commit) ─▶ XADD stream:evaluation ─▶ consumer group "evaluators" (1 consumer per instance,
                                              16 parallel workers, backpressure) ─▶ ScoringEngine ─▶ results row
                                              + attempt_answers outcomes ─▶ ZADD leaderboard:test:{id} ─▶ XACK
 recovery (every 60 s): XCLAIM stale pending → retry, after 5 deliveries → stream:evaluation:dlq
                        + DB sweep of SUBMITTED-but-unevaluated attempts (lost messages)
 final ranks: window closed + 2 min + no unfinished attempts (or 30 min at most) →
              one UPDATE with RANK() / CUME_DIST() → rank_final → result emails
```

**Scoring rules** (`ScoringEngine`, pure and unit-tested)

| Type | Correct | Wrong | Other |
|---|---|---|---|
| Single correct | +marks | −negative | |
| Multiple correct | +marks for the exact set | −negative if **any** wrong option | Partial marking: +marks/4 per correct option chosen (JEE Adv +3/+2/+1). Without it: −negative |
| Numerical | within tolerance (exact if none) | −negative (0 by default) | unparseable = wrong |
| Match | all pairs | −negative | |

- **Unattempted scores 0.** `ANSWERED_AND_MARKED` **is evaluated** (NTA rule). `MARKED_FOR_REVIEW` without an answer is unattempted.
- **"Attempt any N":** only the first N answered questions *in question order* count.
- **Accuracy** = correct / attempted. Only a student's **first attempt is ranked**; re-attempts are practice.

**Rank & percentile.** Ranks are by marks: equal marks means equal rank (1, 2, 2, 4), and time only
orders ties for display. Percentile uses the NTA formula,
`100 × (candidates with score ≤ mine) / total candidates`. Live values come from Redis ZCOUNTs, and the
final values from SQL `RANK()` + `CUME_DIST()`. The two always agree.

**Visibility.** Results are shown immediately unless the test disables it. For **scheduled tests,
solutions and the leaderboard stay hidden until the window closes**, so nobody can leak answers to
students still writing. Staff always see everything.

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/attempts/{id}/result` | Score, rank, percentile (live or final), section and topic breakdown. Poll while `EVALUATING` |
| `GET /api/v1/attempts/{id}/solutions` | Canonical-order review: your answer, key, outcome, marks, time, solution |
| `GET /api/v1/attempts/{id}/comparison` | You vs topper vs average (overall and per section) |
| `GET /api/v1/tests/{id}/leaderboard?limit=100` | Top N (names shortened, e.g. "Riya S.") plus your own rank |
| `GET /api/v1/me/analytics?examCode=` | Score trend, subject strength, weak (<50%) and strong (≥75%) topics (≥3 attempted) |
| `GET /api/v1/admin/dashboard` | Users, tests, live attempts, revenue (30-day daily series, top series), in IST |
| `GET /api/v1/admin/tests/{id}/stats` | Score summary, distribution, per-question difficulty (TOO_HARD / TOO_EASY / SKIPPED) |
| `GET /api/v1/admin/tests/{id}/results` | All results with student names |
| `POST /api/v1/admin/tests/{id}/rankings/finalize` | Freeze ranks now (e.g. always-open tests) |
| `POST /api/v1/admin/attempts/{id}/re-evaluate` | Force re-scoring of one attempt |
| `GET/POST /api/v1/admin/users`, `PATCH …/{id}/status`, `PUT …/{id}/roles` | User admin. Status and role changes revoke that user's sessions instantly |

**Settings.** `EVALUATION_MODE=STREAM|ASYNC` (ASYNC = in-process, for single-node setups or Redis
servers without streams), `app.evaluation.concurrency` (16), `finalize-delay` (2m), `finalize-force-after` (30m).

**Metrics.** `examprep_evaluation_completed_total`, `examprep_evaluation_duration_seconds`.

## Phase 4: the test-taking engine

```
 Start ─▶ Postgres: INSERT attempt (deadline = min(now + duration, test end))     ◀─ unique partial index = 1 running attempt
   │      Redis:    attempt:{id}:meta, ZADD attempts:deadlines
   ▼
 Paper ─▶ L1 JVM (60 s) ─▶ Redis paper:{testId} (6 h) ─▶ build once (lock)   ◀─ pre-warmed on publish / window open
   │      per-student deterministic shuffle (questions within section, options), no answers or solutions
   ▼
 Autosave (every 10–15 s) ─▶ Redis only: one Lua EVAL per batch (seq-ordered, time/visits = max)   ◀─ 0 DB queries
   ▼
 Submit (manual / auto / anti-cheat) ─▶ Redis lock + SELECT FOR UPDATE ─▶ JDBC batch upsert attempt_answers
                                        + attempt_events ─▶ status SUBMITTED ─▶ AttemptSubmittedEvent (after commit)
 Auto-submit: ZRANGEBYSCORE attempts:deadlines every 10 s (parallel) + Postgres sweep every 2 min (fallback)
```

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/tests/{testId}/attempts` | Start, or **resume** the running attempt (idempotent: refresh, second tab, reconnect) |
| `GET /api/v1/attempts/{id}` | Session: shuffled paper, saved answers, `serverNow`, `deadlineAt`, `remainingSeconds`, `lastSeq` |
| `PUT /api/v1/attempts/{id}/answers` | Autosave batch `{changes:[{questionId, seq, answer, markedForReview, timeSpentSeconds, visits}]}` |
| `GET /api/v1/attempts/{id}/time` | Cheap time sync |
| `POST /api/v1/attempts/{id}/events` | Anti-cheat signals (`TAB_SWITCH`, `FULLSCREEN_EXIT`, `COPY`, …) |
| `POST /api/v1/attempts/{id}/submit` | Final submit (idempotent) |
| `GET /api/v1/tests/{testId}/attempts` | My attempts at a test |

**Client contract (for the exam UI in Phase 7)**
- **Timer.** Display `deadlineAt - (clientNow + (serverNow - clientNowAtResponse))`. Never trust the local
  clock alone. Every autosave response re-syncs `remainingSeconds`.
- **`seq`.** A per-attempt counter the client increments on every change and continues from
  `lastSeq + 1` after a reload. Changes with an older seq are ignored, so an offline retry queue can
  replay in any order safely.
- **Answer format.** `{"options":["B"]}`, `{"options":["A","C"]}`, `{"value":"2.5"}`, `{"pairs":{"P":"2"}}`,
  or `null` to clear. Option ids are canonical (A-D) even when displayed shuffled.
- **States are derived by the server** from (answer present, marked). The five NTA colours map to
  `NOT_VISITED` (no entry), `NOT_ANSWERED`, `ANSWERED`, `MARKED_FOR_REVIEW`, `ANSWERED_AND_MARKED`.
- **Errors.** `ATTEMPT_EXPIRED` / `ATTEMPT_NOT_IN_PROGRESS` (409) mean go to the result page.
  `SUBMIT_IN_PROGRESS` means retry in a moment. Rejected changes (bad option, "attempt any N" exceeded)
  come back per question in `rejected[]`, and the rest of the batch still saves.

**Settings (`app.attempt.*`).** `autosave-grace` 15 s, `auto-submit-grace` 30 s,
`max-tab-switches` (`MAX_TAB_SWITCHES`, 0 = log only), `auto-submit-concurrency` 16.

**Metrics.** `examprep_attempt_started_total`, `…_resumed_total`, `…_submitted_total{type}`,
`examprep_attempt_submit_duration_seconds`, `examprep_autosave_requests_total`,
`examprep_autosave_rejected_changes_total`, `examprep_paper_build_seconds`.

**Durability note.** During a test, Redis is the source of truth for answers. Run it with AOF
(`appendfsync everysec`, as in docker-compose) and a replica in production. If Redis state is lost, the
attempt itself is rebuilt from Postgres, but autosaves since the start are lost.

## Phase 3 contents

| Area | What's included |
|---|---|
| Patterns | `JEE_MAIN` (90 Q / 300 / 180 min, Section B numerical "attempt any 5 of 10", no negative), `NEET` (180 Q / 720 / 200 min), `JEE_ADVANCED` (illustrative), `CUSTOM`. A pattern creates typed sections with marking and target counts |
| Test builder | Sections, add questions (paragraphs expand to children, type enforced, duplicates skipped), per-question marks override, drag-and-drop reorder, totals that respect "attempt any N" |
| Auto-generation | Rule-based (topic/chapter/difficulty/type/language, exclude questions used in published tests) and one-click "fill pattern with 30/50/20 mix"; strict mode is all-or-nothing |
| Lifecycle | DRAFT → PUBLISHED → LIVE → COMPLETED (scheduler, cluster-safe `UPDATE … RETURNING`) → ARCHIVED; publish validation (errors vs warnings); structure locked after publish; unpublish only before any attempt |
| Series & access | Free / paid / batch-restricted series, free sample tests inside paid series, validity (days and/or end date), public storefront with per-user `myAccess` |
| Enrollment | Atomic upsert (FREE, PAYMENT, ADMIN, BATCH), admin grant/revoke, "my enrollments" |
| Batches | CRUD, add members by id or email (idempotent), members list |
| Payments | Razorpay orders + checkout signature verify + webhooks (HMAC, de-duplicated, amount cross-check, row-locked, exactly-once fulfilment); **MOCK provider by default** |

### Phase 3 endpoints

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/public/series?examCode=&free=&q=`, `/api/v1/public/series/{slug}` | anyone (adds `myAccess` if logged in) |
| POST | `/api/v1/series/{id}/enroll` (free/batch; paid returns **402**) | student |
| GET | `/api/v1/me/enrollments`, `/api/v1/tests/{id}` (instructions + `hasAccess`) | student |
| POST | `/api/v1/payments/orders` → Checkout options; `/api/v1/payments/verify` | student |
| POST | `/api/v1/payments/{paymentId}/mock-checkout` (MOCK provider only) | student |
| POST | `/api/v1/payments/webhook/razorpay` | Razorpay (HMAC) |
| GET | `/api/v1/payments/mine`, `/api/v1/admin/payments` | student / ADMIN |
| CRUD | `/api/v1/admin/series` (+ `/publish`, `/archive`, `/unpublish`, `/enrollments`) | ADMIN (teachers read) |
| CRUD | `/api/v1/admin/tests` (+ `/validation`, `/publish`, `/unpublish`, `/archive`, `/patterns`) | staff |
| — | `/api/v1/admin/tests/{id}/sections[/{sid}[/questions\|/order]]`, `/questions/{tqId}`, `/generate`, `/generate-from-pattern` | staff |
| CRUD | `/api/v1/admin/batches` (+ `/members`) | ADMIN (teachers read) |

### Payments: mock vs Razorpay

- **Dev default `PAYMENT_PROVIDER=MOCK`.** `POST /payments/orders` returns `mock: true`. The frontend then
  calls `POST /payments/{paymentId}/mock-checkout` instead of opening Razorpay. Signatures use the same
  HMAC scheme, so the verify code path is identical.
- **Razorpay:** set `PAYMENT_PROVIDER=RAZORPAY`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and
  `RAZORPAY_WEBHOOK_SECRET`. In the Razorpay dashboard add the webhook `https://<host>/api/v1/payments/webhook/razorpay`
  for `payment.captured`, `payment.failed` and `order.paid`. Locally, expose it with a tunnel such as ngrok.
- Access is granted on **either** a verified checkout **or** the webhook, whichever arrives first. Both
  lock the payment row, so fulfilment happens exactly once.

### Dev seed (Phase 3)

| Series | Price | Notes |
|---|---|---|
| JEE Main Free Starter Series | free | the demo student is enrolled |
| JEE Main Full Mock Series 2027 | ₹499 / 180 days | 1 paid test + 1 free sample test (for payment testing) |
| Batch A Weekly Tests | ₹999, batch-restricted | demo student is in batch `JEE-2027-A`, so gets it free; hidden from others |

## Phase 2 contents

| Area | What's included |
|---|---|
| Catalog | Exam → Subject → Chapter → Topic. Public tree is Redis-cached (typed JSON, evicted **after commit**). Soft-delete via `active=false` |
| Question bank | SINGLE_CORRECT, MULTIPLE_CORRECT, NUMERICAL (value ± tolerance), MATCH, PARAGRAPH (+ child questions). Typed JSONB content/answer key, per-type validation, tags, EN/HI, search with filters and trigram-indexed text search |
| Integrity | Teachers edit only their own questions; answer key and type are **frozen** once a question is in a published test; archive instead of delete |
| Bulk import | `.xlsx`/`.csv`, all-or-nothing with per-row errors, true dry run (inserts then rolls back), optional auto-create of chapters/topics, downloadable template |
| Files | S3/MinIO uploads with magic-byte type detection (no SVG), server-generated keys, public (CDN-cacheable) vs private (presigned URL) categories |

### Phase 2 endpoints

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/public/catalog/exams` | anyone |
| GET | `/api/v1/public/catalog/exams/{code}/tree` | anyone |
| GET | `/api/v1/admin/catalog/exams`, `/exams/{id}/tree?includeInactive=` | staff |
| POST/PUT | `/api/v1/admin/catalog/exams`, `/subjects` | ADMIN |
| POST/PUT | `/api/v1/admin/catalog/chapters`, `/topics` | staff |
| GET/POST | `/api/v1/admin/questions` (search filters: `examId, subjectId, chapterId, topicId, parentId, type, difficulty, language, status, tag, q, mine`) | staff |
| GET/PUT/DELETE | `/api/v1/admin/questions/{id}` | staff (teachers: own only) |
| POST | `/api/v1/admin/questions/import?dryRun=&autoCreateCatalog=` (multipart `file`) | staff |
| GET | `/api/v1/admin/questions/import/template` | staff |
| POST | `/api/v1/admin/files?category=QUESTION_IMAGE\|THUMBNAIL\|SOLUTION_PDF` (multipart `file`) | staff |
| GET | `/api/v1/admin/files/presigned-url?key=` | staff |

Staff = ADMIN or TEACHER.

```bash
# Import questions from the template (dry run first)
curl -s localhost:8080/api/v1/admin/questions/import/template -H "Authorization: Bearer $TOKEN" -o tpl.csv
curl -s -X POST "localhost:8080/api/v1/admin/questions/import?dryRun=true" -H "Authorization: Bearer $TOKEN" -F file=@tpl.csv

# Upload a question image, then put the returned url into content.images[].url
curl -s -X POST "localhost:8080/api/v1/admin/files?category=QUESTION_IMAGE" -H "Authorization: Bearer $TOKEN" -F file=@diagram.png
```

**Import columns:** `examCode, subjectCode, chapter, topic, type, questionText` and `correctAnswer` are required.
Optional: `difficulty, language, imageUrl, optionA..optionF, tolerance, marks, negativeMarks, solutionText,
solutionVideoUrl, tags (a;b), source, year`. Headers are matched loosely ("Question Text" = `questionText`).
Types can also be written as `SCQ`/`MCQ`/`INTEGER`. Quote any cell containing a comma. That includes the
LaTeX thin space `\,`.

## Phase 1 contents

| Area | What's included |
|---|---|
| Schema | Full Flyway schema for all modules (`V1__init.sql`), `attempt_answers` hash-partitioned ×16, reference data (`V2`), dev sample data (`db/devdata/V1000`) |
| Common | `ApiResponse` envelope, `ErrorCode` catalogue, global exception handler, UUIDv7 ids, JPA auditing, request-id correlation, Redis rate limiter (`@RateLimit`) |
| Security | JWT access (15 min) + single-use rotating refresh (7 d), Redis blacklist, per-user token generation (revoke-all), optional single active session for students, method security |
| Auth | register, login (email **or** phone), refresh, logout, forgot/reset password |
| User | `GET/PATCH /users/me`, change password |
| Notification | Channel-agnostic sender interface, SMTP email, templates, persisted log, SKIP LOCKED retry job |
| DevOps | Dockerfile, docker-compose (Postgres, Redis, MinIO, Mailpit, pgAdmin), `.env.example`, GitHub Actions CI |

## Prerequisites

- JDK 21
- Docker Desktop (for docker-compose **and** for the Testcontainers integration tests)
- Maven is **not** required. Use the wrapper (`mvnw` / `mvnw.cmd`).

## Run locally

```bash
cp .env.example .env                                   # optional overrides

# 1. Infra only
docker compose up -d postgres redis minio minio-init mailpit

# 2. Backend (dev profile: sample data, debug logs)
./mvnw spring-boot:run                                 # Windows: mvnw.cmd spring-boot:run

# ...or everything in containers
docker compose up -d --build
```

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | API docs (click **Authorize**, paste the `accessToken`) |
| http://localhost:8080/actuator/health | Health (db + redis) |
| http://localhost:8080/actuator/prometheus | Metrics |
| http://localhost:8025 | Mailpit, which catches all outgoing email |
| http://localhost:9101 | MinIO console (minioadmin / minioadmin). The S3 API is on **9100**, not 9000, because port 9000 is commonly taken (e.g. by the Zscaler client) |
| http://localhost:5050 | pgAdmin (`docker compose --profile tools up -d`) |

### Seeded accounts

| Role | Email | Phone | Password | Env |
|---|---|---|---|---|
| ADMIN | admin@examprep.local | — | `Admin@123` (from `ADMIN_INITIAL_PASSWORD`) | all |
| TEACHER | teacher@examprep.local | 9000000002 | `Teacher@123` | dev/test only |
| STUDENT | student@examprep.local | 9000000003 | `Student@123` | dev/test only |

The dev data also seeds JEE Main chapters/topics, 10 LaTeX questions, and the free
**"JEE Main Free Starter Series"** with a published 10-question test. The demo student is enrolled in it.

The series also has a **"Diagram Practice Test (figures & graphs)"**, seeded by `V1002` with 6 questions that use figures: an incline diagram, a v–t graph, a projectile path, an energy-level diagram, a parabola, and picture options for molecular shapes. A free-body diagram appears in one solution. It allows 3 attempts. The SVG figures live in `frontend/public/samples/` and are served at `/samples/…`. Regenerate them with `node frontend/scripts/generate-sample-figures.cjs`.

## Try the auth flow

```bash
# Login (email or phone)
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"identifier":"student@examprep.local","password":"Student@123"}'

# Use the token
curl -s localhost:8080/api/v1/users/me -H "Authorization: Bearer <accessToken>"

# Rotate tokens (the old refresh token becomes unusable)
curl -s -X POST localhost:8080/api/v1/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# Forgot password, then open Mailpit (localhost:8025) for the reset link
curl -s -X POST localhost:8080/api/v1/auth/forgot-password -H 'Content-Type: application/json' \
  -d '{"email":"student@examprep.local"}'
```

### Auth error codes the frontend should handle

| `error.code` | HTTP | Client action |
|---|---|---|
| `TOKEN_EXPIRED` | 401 | Call `/auth/refresh`, then retry the request |
| `TOKEN_INVALID` / `SESSION_REVOKED` | 401 | Clear tokens and go to login |
| `FORBIDDEN` | 403 | Show "no access" |
| `RATE_LIMITED` | 429 | Wait `Retry-After` seconds |

## Tests

```bash
./mvnw test      # unit tests always; Testcontainers integration tests run when Docker is available
./mvnw verify    # same + packaging (what CI runs)
```

Integration tests start real `postgres:16-alpine`, `redis:7-alpine` and `minio` containers once and share
them across test classes. Without Docker they are **skipped**, not failed.

## Key design decisions (so far)

- **UUIDv7 primary keys**: globally unique and non-enumerable, and time-ordered, so B-tree inserts stay append-mostly.
- **`attempt_answers` hash-partitioned by `attempt_id` (16 partitions).** Every hot query filters by attempt,
  so it prunes to one partition, and writes spread evenly. The rationale is written up in `V1__init.sql`.
- **Stateless JWT with a tiny amount of Redis state.** Each request costs one pipelined round trip: blacklist
  check plus the user's token generation. Bumping the generation revokes all of a user's tokens at once
  (password reset, account disable). If Redis is down the revocation check *fails open*, because access
  tokens are short-lived and a Redis blip must not log out students mid-exam.
- **Prod-safe seeding.** Only roles, the admin, exams and subjects go to prod. Demo data lives in
  `db/devdata`, which only the dev/test profiles load. The admin password is a Flyway placeholder taken from the environment.
- **Redis `noeviction` + AOF.** Later phases buffer live exam answers in Redis, so memory pressure must
  fail writes loudly instead of silently evicting answers.
- **Typed JSONB.** Question content and answer keys are Java records, not raw JSON, so they are
  validated on the way in. The answer key is canonicalised (sorted options, normalised decimals), so
  "did the key change?" and evaluation are simple equality checks.
- **Cache is an optimisation, never a dependency.** Redis errors in `@Cacheable` degrade to DB reads.
  Catalog eviction runs after commit, so a concurrent read cannot re-cache stale data.
- **Catalog links are FK columns, not JPA associations.** Trees load in one query per level, and other
  modules hold ids without depending on catalog entities.
- **Module dependencies point one way.** `payment → test → enrollment/batch/question → catalog`. Other
  modules use the question bank through `QuestionLookupService` and never touch its repository.
  `SeriesAccessService` is the single place access rules live.
- **Provider calls never hold a DB connection.** Payment orders are persisted, the provider is called
  outside any transaction, and the result is persisted, so a slow gateway cannot exhaust the pool at exam-launch peaks.
