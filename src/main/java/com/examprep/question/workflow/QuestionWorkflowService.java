package com.examprep.question.workflow;

import com.examprep.audit.service.AuditContext;
import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.config.AppProperties;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.outbox.NotificationOutbox;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.QuestionSnapshot;
import com.examprep.question.repository.QuestionRepository;
import com.examprep.question.service.QuestionVersionService;
import com.examprep.question.workflow.QuestionActivityService.Kind;
import com.examprep.question.workflow.StudioDtos.BulkAction;
import com.examprep.question.workflow.StudioDtos.BulkFailure;
import com.examprep.question.workflow.StudioDtos.BulkResult;
import com.examprep.question.workflow.StudioDtos.PublishResult;
import com.examprep.question.workflow.StudioDtos.QueueCounts;
import com.examprep.question.workflow.StudioDtos.ReviewerDto;
import com.examprep.rbac.service.PermissionService;
import com.examprep.rbac.service.SubjectScopeGuard;
import com.examprep.security.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Review workflow of questions: submit, assign/claim, request changes, approve, publish,
 * archive/restore, comments, bulk actions and the queue counts.
 *
 * <p>Four-eyes rules: nobody reviews or approves a question they submitted, and the person
 * who saved the current version cannot approve it (a reviewer who fixes a typo during review
 * needs a second reviewer to approve). With {@code content.review.required = false},
 * publishers may publish drafts directly (small teams, trusted PYQ imports).
 *
 * <p>Publishing makes the current version the live one. Draft tests that use the question
 * move to it; published tests move only on request and only if the new version scores every
 * answer the same way. Otherwise they keep the version students were tested on.
 */
@Slf4j
@Service
public class QuestionWorkflowService {

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private final QuestionRepository repository;
    private final PermissionService permissions;
    private final SubjectScopeGuard scopes;
    private final ContentSettingsService settings;
    private final ReviewerDirectory reviewers;
    private final QuestionActivityService activity;
    private final QuestionVersionService versions;
    private final TestPinUpdater pins;
    private final NotificationOutbox notifications;
    private final JdbcTemplate jdbc;
    private final CatalogQueryService catalog;
    private final AppProperties app;
    private final Clock clock;
    private final TransactionTemplate perItem;

    public QuestionWorkflowService(QuestionRepository repository, PermissionService permissions,
                                   SubjectScopeGuard scopes, ContentSettingsService settings,
                                   ReviewerDirectory reviewers, QuestionActivityService activity,
                                   QuestionVersionService versions, TestPinUpdater pins,
                                   NotificationOutbox notifications, JdbcTemplate jdbc, CatalogQueryService catalog,
                                   AppProperties app, Clock clock, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.permissions = permissions;
        this.scopes = scopes;
        this.settings = settings;
        this.reviewers = reviewers;
        this.activity = activity;
        this.versions = versions;
        this.pins = pins;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.app = app;
        this.clock = clock;
        this.perItem = new TransactionTemplate(txManager);
        this.perItem.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ what may the caller do?

    public Set<QuestionAction> allowed(Question q, AuthUser u) {
        EnumSet<QuestionAction> a = EnumSet.noneOf(QuestionAction.class);
        QuestionStatus s = q.getStatus();
        boolean inScope = inScope(u, q.getSubjectId());
        boolean canEdit = inScope && canModify(q, u);
        boolean submitter = u.id().equals(q.getSubmittedBy());

        if (canEdit && s != QuestionStatus.ARCHIVED) {
            a.add(QuestionAction.EDIT);
        }
        if (canEdit && s.canSubmit()) {
            a.add(QuestionAction.SUBMIT);
        }
        if (s == QuestionStatus.IN_REVIEW) {
            boolean reviewer = has(u, "question.review") && inScope && !submitter;
            if (reviewer && !u.id().equals(q.getReviewerId())) {
                a.add(QuestionAction.CLAIM);
            }
            if (reviewer) {
                a.add(QuestionAction.REQUEST_CHANGES);
            }
            if (has(u, "question.approve")) {
                a.add(QuestionAction.ASSIGN);
                if (inScope && !submitter && !isLastEditor(q, u)) {
                    a.add(QuestionAction.APPROVE);
                }
            }
        }
        if (has(u, "question.publish") && inScope && publishable(q)) {
            a.add(QuestionAction.PUBLISH);
        }
        if (has(u, "question.archive") && canEdit) {
            a.add(s == QuestionStatus.ARCHIVED ? QuestionAction.RESTORE : QuestionAction.ARCHIVE);
        }
        if (inScope && (canEdit || has(u, "question.review"))) {
            a.add(QuestionAction.COMMENT);
        }
        return a;
    }

    /** Owner-or-"update any", used by edits, archive and restore. Scope is checked separately. */
    public boolean canModify(Question q, AuthUser u) {
        return has(u, "question.update.any") || (has(u, "question.update") && u.id().equals(q.getCreatedBy()));
    }

    /** Throws unless the caller may edit the question (ownership + subject scope). */
    public void requireModify(Question q, AuthUser u) {
        if (!canModify(q, u)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You can modify only your own questions");
        }
        scopes.assertCanAuthor(u, q.getSubjectId());
    }

    // ------------------------------------------------------------------ edits (called by QuestionService)

    /**
     * Status after a content edit. Editing an approved or published question starts a new
     * revision that must be reviewed again; the published version stays live meanwhile.
     */
    public void afterEdit(Question q, AuthUser u, int version, boolean created) {
        if (created) {
            activity.log(q.getId(), version, u.id(), Kind.CREATED, null, null, null);
            return;
        }
        QuestionStatus before = q.getStatus();
        String note = null;
        if (before == QuestionStatus.APPROVED) {
            note = "Changed after approval, so it needs to be reviewed again.";
            q.setStatus(QuestionStatus.DRAFT);
            q.clearReview();
        } else if (before == QuestionStatus.PUBLISHED) {
            note = "New revision v" + version + " started; v" + q.getPublishedVersion() + " stays live until it is published.";
            q.setStatus(QuestionStatus.DRAFT);
            q.clearReview();
        } else if (before == QuestionStatus.IN_REVIEW) {
            note = "Edited during review.";
        }
        activity.log(q.getId(), version, u.id(), Kind.EDITED, note, null, Map.of("from", before.name()));
    }

    // ------------------------------------------------------------------ transitions

    @Transactional
    public void submit(UUID id, UUID assigneeId, String note, AuthUser u) {
        doSubmit(load(id), assigneeId, note, u);
    }

    @Transactional
    public void claim(UUID id, AuthUser u) {
        Question q = load(id);
        requireReviewer(q, u);
        UUID previous = q.getReviewerId();
        q.setReviewerId(u.id());
        activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.ASSIGNED, null, null,
                meta("assignee", u.id(), "previous", previous, "claimed", true));
        audit("question.review.claim", q);
    }

    @Transactional
    public void assign(UUID id, UUID assigneeId, AuthUser u) {
        Question q = load(id);
        requirePermission(u, "question.approve", "assign reviewers");
        requireStatus(q, QuestionStatus.IN_REVIEW, "Only questions in review can be assigned");
        if (assigneeId != null) {
            requireEligible(q, assigneeId);
        }
        UUID previous = q.getReviewerId();
        q.setReviewerId(assigneeId);
        activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.ASSIGNED, null, null,
                meta("assignee", assigneeId, "previous", previous));
        if (assigneeId != null && !assigneeId.equals(previous)) {
            notifyAssigned(q, assigneeId);
        }
        audit("question.review.assign", q);
    }

    @Transactional
    public void requestChanges(UUID id, String comment, AuthUser u) {
        doRequestChanges(load(id), comment, u);
    }

    @Transactional
    public Optional<PublishResult> approve(UUID id, String comment, boolean publish, AuthUser u) {
        return doApprove(load(id), comment, publish, u);
    }

    @Transactional
    public PublishResult publish(UUID id, Boolean propagate, AuthUser u) {
        Question q = load(id);
        requirePublish(q, u);
        return doPublish(q, u, propagate == null || propagate);
    }

    @Transactional
    public void archive(UUID id, AuthUser u) {
        doArchive(load(id), u, null);
    }

    @Transactional
    public void restore(UUID id, AuthUser u) {
        Question q = load(id);
        requireModify(q, u);
        requireStatus(q, QuestionStatus.ARCHIVED, "Only archived questions can be restored");
        boolean live = q.getPublishedVersion() != null && q.getPublishedVersion() == q.getCurrentVersion();
        q.setStatus(live ? QuestionStatus.PUBLISHED : QuestionStatus.DRAFT);
        activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.RESTORED, null, null,
                meta("status", q.getStatus().name()));
        audit("question.restore", q);
    }

    // ------------------------------------------------------------------ comments

    @Transactional
    public UUID comment(UUID id, String body, String field, AuthUser u) {
        Question q = load(id);
        if (!allowed(q, u).contains(QuestionAction.COMMENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot comment on this question");
        }
        return activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.COMMENT, body, field, null);
    }

    @Transactional
    public void resolveComment(UUID id, UUID commentId, boolean resolved, AuthUser u) {
        Question q = load(id);
        QuestionActivityService.CommentRef c = activity.comment(id, commentId)
                .orElseThrow(() -> NotFoundException.of("Comment", commentId));
        boolean own = u.id().equals(c.authorId());
        boolean editor = inScope(u, q.getSubjectId()) && canModify(q, u);
        if (!own && !editor && !has(u, "question.review")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot resolve this comment");
        }
        activity.resolve(commentId, u.id(), resolved);
    }

    // ------------------------------------------------------------------ bulk

    /** Applies one action to many questions; each question succeeds or fails on its own. */
    public BulkResult bulk(BulkAction action, List<UUID> ids, String comment, AuthUser u) {
        int ok = 0;
        List<BulkFailure> failed = new ArrayList<>();
        for (UUID id : ids.stream().distinct().toList()) {
            try {
                perItem.executeWithoutResult(s -> {
                    Question q = load(id);
                    switch (action) {
                        case SUBMIT -> doSubmit(q, null, comment, u);
                        case APPROVE -> doApprove(q, comment, false, u);
                        case PUBLISH -> {
                            requirePublish(q, u);
                            doPublish(q, u, true);
                        }
                        case ARCHIVE -> {
                            requirePermission(u, "question.archive", "archive questions");
                            doArchive(q, u, comment);
                        }
                    }
                });
                ok++;
            } catch (BusinessException e) {
                failed.add(new BulkFailure(id, e.getMessage()));
            }
        }
        AuditContext.action("question.bulk." + action.name().toLowerCase(Locale.ROOT));
        AuditContext.meta("requested", ids.size());
        AuditContext.meta("succeeded", ok);
        if (comment != null && !comment.isBlank()) {
            AuditContext.meta("comment", comment);
        }
        return new BulkResult(ok, failed);
    }

    // ------------------------------------------------------------------ queues

    public QueueCounts counts(AuthUser u) {
        Optional<Set<UUID>> scope = scopes.scope(u);
        String where = scope.map(s -> " where subject_id = any (?)").orElse("");
        List<Object> args = new ArrayList<>(List.of(u.id(), u.id(), u.id()));
        scope.ifPresent(s -> args.add(s.toArray(new UUID[0])));
        return jdbc.queryForObject("""
                select count(*) filter (where status = 'IN_REVIEW' and reviewer_id = ?),
                       count(*) filter (where status = 'IN_REVIEW' and reviewer_id is null),
                       count(*) filter (where status = 'IN_REVIEW' and review_due_at < now()),
                       count(*) filter (where status = 'IN_REVIEW'),
                       count(*) filter (where status = 'CHANGES_REQUESTED' and created_by = ?),
                       count(*) filter (where status = 'APPROVED'),
                       count(*) filter (where status = 'DRAFT' and created_by = ?)
                from questions""" + where, (rs, i) -> new QueueCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)), args.toArray());
    }

    public List<ReviewerDto> reviewersFor(UUID questionId) {
        Question q = load(questionId);
        return reviewers.eligible(q.getSubjectId(), q.getSubmittedBy());
    }

    // ------------------------------------------------------------------ SLA

    /**
     * Sends one overdue reminder per review. The UPDATE ... RETURNING claims the rows
     * atomically, so several instances never send the same reminder twice.
     */
    @Transactional
    public int sendOverdueReminders() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                update questions set review_overdue_sent = true
                where status = 'IN_REVIEW' and review_due_at < now() and not review_overdue_sent
                returning id, reviewer_id, subject_id, review_due_at, content::text as content
                """);
        for (Map<String, Object> row : due) {
            UUID id = (UUID) row.get("id");
            UUID reviewer = (UUID) row.get("reviewer_id");
            activity.log(id, null, null, Kind.OVERDUE, "The review is past its due time.", null, null);
            Question q = repository.findById(id).orElse(null);
            if (q == null) {
                continue;
            }
            List<UUID> recipients = reviewer != null ? List.of(reviewer)
                    : reviewers.eligible(q.getSubjectId(), q.getSubmittedBy()).stream().limit(10)
                    .map(ReviewerDto::id).toList();
            for (UUID r : recipients) {
                email(r, NotificationTemplate.QUESTION_REVIEW_OVERDUE, vars(q, "dueAt", DUE_FORMAT.format(q.getReviewDueAt())));
            }
        }
        return due.size();
    }

    // ------------------------------------------------------------------ transition bodies

    private void doSubmit(Question q, UUID assigneeId, String note, AuthUser u) {
        requireModify(q, u);
        if (!q.getStatus().canSubmit()) {
            throw workflow("Only drafts and questions sent back for changes can be submitted for review; "
                    + "this one is " + label(q.getStatus()));
        }
        UUID reviewer = null;
        if (assigneeId != null) {
            if (assigneeId.equals(u.id())) {
                throw new BusinessException(ErrorCode.QUESTION_SELF_REVIEW, "Choose a reviewer other than yourself");
            }
            requireEligible(q, assigneeId);
            reviewer = assigneeId;
        } else if (q.getReviewerId() != null && !q.getReviewerId().equals(u.id())
                && reviewers.isEligible(q.getReviewerId(), q.getSubjectId())) {
            reviewer = q.getReviewerId();     // back to whoever asked for the changes
        } else if (settings.get().autoAssign()) {
            reviewer = reviewers.pickFor(q.getSubjectId(), u.id()).map(ReviewerDto::id).orElse(null);
        }
        Instant now = clock.instant();
        q.setStatus(QuestionStatus.IN_REVIEW);
        q.setSubmittedBy(u.id());
        q.setReviewerId(reviewer);
        q.setReviewRequestedAt(now);
        q.setReviewDueAt(now.plus(Duration.ofHours(settings.get().slaHours())));
        q.setReviewOverdueSent(false);
        activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.SUBMITTED, note, null,
                meta("assignee", reviewer, "dueAt", q.getReviewDueAt().toString()));
        if (reviewer != null) {
            notifyAssigned(q, reviewer);
        }
        audit("question.submit", q);
    }

    private void doRequestChanges(Question q, String comment, AuthUser u) {
        requireReviewer(q, u);
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Say what needs to change");
        }
        q.setStatus(QuestionStatus.CHANGES_REQUESTED);
        q.setReviewerId(u.id());
        q.setReviewDueAt(null);
        activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.CHANGES_REQUESTED, comment, null, null);
        UUID author = q.getSubmittedBy() != null ? q.getSubmittedBy() : q.getCreatedBy();
        if (author != null) {
            email(author, NotificationTemplate.QUESTION_CHANGES_REQUESTED,
                    vars(q, "reviewer", nameOf(u.id()), "comment", comment));
        }
        audit("question.request-changes", q);
    }

    private Optional<PublishResult> doApprove(Question q, String comment, boolean publish, AuthUser u) {
        requirePermission(u, "question.approve", "approve questions");
        scopes.assertCanAuthor(u, q.getSubjectId());
        requireStatus(q, QuestionStatus.IN_REVIEW, "Only questions in review can be approved");
        if (u.id().equals(q.getSubmittedBy())) {
            throw new BusinessException(ErrorCode.QUESTION_SELF_REVIEW, "You cannot approve a question you submitted");
        }
        if (isLastEditor(q, u)) {
            throw new BusinessException(ErrorCode.QUESTION_SELF_REVIEW,
                    "You saved the current version, so another reviewer has to approve it");
        }
        if (publish) {
            requirePermission(u, "question.publish", "publish questions");
        }
        q.setStatus(QuestionStatus.APPROVED);
        q.setReviewerId(u.id());
        q.setReviewDueAt(null);
        activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.APPROVED, comment, null, null);
        UUID author = q.getSubmittedBy() != null ? q.getSubmittedBy() : q.getCreatedBy();
        if (author != null) {
            email(author, NotificationTemplate.QUESTION_APPROVED,
                    vars(q, "reviewer", nameOf(u.id()), "published", publish ? " and published it" : ""));
        }
        audit("question.approve", q);
        return publish ? Optional.of(doPublish(q, u, true)) : Optional.empty();
    }

    private PublishResult doPublish(Question q, AuthUser u, boolean propagate) {
        Integer previous = q.getPublishedVersion();
        int version = q.getCurrentVersion();
        q.setPublishedVersion(version);
        q.setPublishedAt(clock.instant());
        q.setStatus(QuestionStatus.PUBLISHED);
        q.clearReview();
        repository.flush();
        versions.markPublished(q.getId(), version, u.id());

        TestPinUpdater.Result moved = TestPinUpdater.Result.NONE;
        if (previous != null && previous != version) {
            boolean paragraph = q.getType() == QuestionType.PARAGRAPH;
            QuestionSnapshot next = versions.load(q.getId(), version).orElseThrow();
            Map<Integer, Boolean> memo = new HashMap<>();
            moved = pins.moveToVersion(q.getId(), version, paragraph, propagate, pinned -> paragraph
                    || memo.computeIfAbsent(pinned, v -> versions.load(q.getId(), v).map(next::scoresLike)
                    .orElse(false)));
        }
        activity.log(q.getId(), version, u.id(), Kind.PUBLISHED, null, null, meta("version", version,
                "previous", previous, "draftTestsUpdated", moved.draftTestsUpdated(),
                "liveTestsUpdated", moved.liveTestsUpdated(), "liveTestsKept", moved.liveTestsKept()));
        audit("question.publish", q);
        AuditContext.meta("version", version);
        return new PublishResult(version, previous, moved.draftTestsUpdated(), moved.liveTestsUpdated(),
                moved.liveTestsKept());
    }

    private void doArchive(Question q, AuthUser u, String reason) {
        requireModify(q, u);
        if (q.getStatus() == QuestionStatus.ARCHIVED) {
            throw workflow("The question is already archived");
        }
        QuestionStatus before = q.getStatus();
        q.setStatus(QuestionStatus.ARCHIVED);
        q.clearReview();
        activity.log(q.getId(), q.getCurrentVersion(), u.id(), Kind.ARCHIVED, reason, null, meta("from", before.name()));
        audit("question.archive", q);
    }

    // ------------------------------------------------------------------ guards

    private boolean publishable(Question q) {
        QuestionStatus s = q.getStatus();
        if (s == QuestionStatus.APPROVED) {
            return true;
        }
        return s.canSubmit() && !settings.get().reviewRequired();
    }

    private void requirePublish(Question q, AuthUser u) {
        requirePermission(u, "question.publish", "publish questions");
        scopes.assertCanAuthor(u, q.getSubjectId());
        if (!publishable(q)) {
            throw workflow(q.getStatus() == QuestionStatus.PUBLISHED
                    ? "This version is already published"
                    : "Only approved questions can be published (this one is " + label(q.getStatus()) + ")");
        }
    }

    private void requireReviewer(Question q, AuthUser u) {
        requirePermission(u, "question.review", "review questions");
        scopes.assertCanAuthor(u, q.getSubjectId());
        requireStatus(q, QuestionStatus.IN_REVIEW, "The question is not in review");
        if (u.id().equals(q.getSubmittedBy())) {
            throw new BusinessException(ErrorCode.QUESTION_SELF_REVIEW, "You cannot review a question you submitted");
        }
    }

    private void requireEligible(Question q, UUID assigneeId) {
        if (assigneeId.equals(q.getSubmittedBy()) || !reviewers.isEligible(assigneeId, q.getSubjectId())) {
            throw new BusinessException(ErrorCode.REVIEWER_NOT_ELIGIBLE);
        }
    }

    private void requirePermission(AuthUser u, String permission, String what) {
        if (!has(u, permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not have permission to " + what);
        }
    }

    private static void requireStatus(Question q, QuestionStatus expected, String message) {
        if (q.getStatus() != expected) {
            throw workflow(message + " (it is " + label(q.getStatus()) + ")");
        }
    }

    private boolean isLastEditor(Question q, AuthUser u) {
        return versions.author(q.getId(), q.getCurrentVersion()).map(u.id()::equals).orElse(false);
    }

    private boolean inScope(AuthUser u, UUID subjectId) {
        return scopes.scope(u).map(s -> s.contains(subjectId)).orElse(true);
    }

    private boolean has(AuthUser u, String permission) {
        return permissions.has(u, permission);
    }

    private Question load(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Question", id));
    }

    private static BusinessException workflow(String message) {
        return new BusinessException(ErrorCode.QUESTION_WORKFLOW, message);
    }

    static String label(QuestionStatus s) {
        return s.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static void audit(String action, Question q) {
        AuditContext.action(action);
        AuditContext.entity("QUESTION", q.getId());
        AuditContext.meta("status", q.getStatus().name());
    }

    // ------------------------------------------------------------------ notifications

    private void notifyAssigned(Question q, UUID reviewerId) {
        email(reviewerId, NotificationTemplate.QUESTION_REVIEW_ASSIGNED, vars(q,
                "submitter", q.getSubmittedBy() == null ? "Someone" : nameOf(q.getSubmittedBy()),
                "subject", subjectName(q),
                "dueAt", q.getReviewDueAt() == null ? "-" : DUE_FORMAT.format(q.getReviewDueAt())));
    }

    private void email(UUID userId, NotificationTemplate template, Map<String, Object> vars) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select email, full_name from users where id = ? and status = 'ACTIVE'", userId);
        if (rows.isEmpty() || rows.getFirst().get("email") == null) {
            return;
        }
        Map<String, Object> all = new LinkedHashMap<>(vars);
        all.put("name", rows.getFirst().get("full_name"));
        notifications.enqueue(userId, NotificationChannel.EMAIL, template, (String) rows.getFirst().get("email"), all);
    }

    private Map<String, Object> vars(Question q, Object... pairs) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("preview", preview(q));
        v.put("questionUrl", app.frontendUrl() + "/admin/questions/" + q.getId());
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            v.put((String) pairs[i], pairs[i + 1] == null ? "-" : pairs[i + 1]);
        }
        return v;
    }

    private String nameOf(UUID userId) {
        List<String> n = jdbc.queryForList("select full_name from users where id = ?", String.class, userId);
        return n.isEmpty() ? "Someone" : n.getFirst();
    }

    private String subjectName(Question q) {
        if (q.getTopicId() == null) {
            return "";
        }
        TopicPath p = catalog.resolveTopics(Set.of(q.getTopicId())).get(q.getTopicId());
        return p == null ? "" : p.subjectName();
    }

    private static String preview(Question q) {
        String t = q.getContent().text() != null ? q.getContent().text() : q.getContent().paragraph();
        if (t == null) {
            return "(no text)";
        }
        String c = t.replaceAll("\\s+", " ").trim();
        return c.length() <= 80 ? c : c.substring(0, 80) + "…";
    }

    /** Map.of with nullable values. */
    private static Map<String, Object> meta(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                m.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return m;
    }
}
