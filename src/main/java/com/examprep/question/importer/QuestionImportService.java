package com.examprep.question.importer;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogAdminService;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.question.dto.ImportReport;
import com.examprep.question.dto.ImportReport.RowError;
import com.examprep.question.dto.QuestionRequest;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.repository.QuestionRepository;
import com.examprep.question.service.QuestionService;
import com.examprep.question.service.QuestionVersionService;
import com.examprep.question.workflow.ContentSettingsService;
import com.examprep.rbac.service.PermissionService;
import com.examprep.rbac.service.SubjectScopeGuard;
import com.examprep.security.AuthUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bulk question import from Excel or CSV.
 *
 * <p>The whole import runs in <b>one transaction</b>:
 * <ol>
 *   <li>Parse the sheet and check that the required columns exist.</li>
 *   <li>For every row: resolve the catalog path (optionally auto-creating the chapter
 *       and topic), map the row, run Bean Validation and the domain validator. Errors
 *       are collected instead of stopping at the first one.</li>
 *   <li>If any row failed, roll back everything and return the error report.</li>
 *   <li>Otherwise batch-insert all questions (JDBC batching, pre-generated UUIDs).</li>
 * </ol>
 * A <b>dry run</b> performs every step, including the INSERTs, so DB constraints are
 * exercised too, and then rolls back. "Dry run passed" therefore reliably means
 * "import will pass".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportService {

    public static final int MAX_ROWS = 2_000;
    public static final int MAX_REPORTED_ERRORS = 200;

    private final CatalogAdminService catalogAdmin;
    private final QuestionService questionService;
    private final QuestionRepository questionRepository;
    private final Validator beanValidator;
    private final SubjectScopeGuard scopes;
    private final QuestionVersionService versions;
    private final ContentSettingsService settings;
    private final PermissionService permissions;
    private final Clock clock;

    /**
     * Workflow state of imported questions. SUBMIT puts them in the <em>unassigned</em> review
     * queue (reviewers claim them; no e-mail per question). PUBLISH is allowed only when review
     * is not required and the importer may publish.
     */
    public enum AfterImport { DRAFT, SUBMIT, PUBLISH }

    @Transactional
    public ImportReport importQuestions(MultipartFile file, boolean dryRun, boolean autoCreateCatalog,
                                        AfterImport after, AuthUser user) {
        if (after == AfterImport.PUBLISH && (!permissions.has(user, "question.publish") || settings.get().reviewRequired())) {
            throw new BusinessException(ErrorCode.QUESTION_WORKFLOW, "Imported questions can be published directly only "
                    + "when review is not required and you may publish. Import them as drafts or submit them for review.");
        }
        SpreadsheetParser.ParsedSheet sheet;
        try (InputStream in = file.getInputStream()) {
            sheet = SpreadsheetParser.parse(file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Could not read upload: " + e.getMessage());
        }

        List<String> missing = ImportColumns.REQUIRED.stream().filter(c -> !sheet.headers().contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Missing required columns: " + missing);
        }
        List<ImportRow> rows = sheet.rows();
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The sheet has no data rows");
        }
        if (rows.size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Too many rows (" + rows.size() + "); split into files of at most " + MAX_ROWS);
        }

        Map<String, TopicPath> topicCache = new HashMap<>();
        List<Question> valid = new ArrayList<>(rows.size());
        List<RowError> errors = new ArrayList<>();

        for (ImportRow row : rows) {
            try {
                TopicPath path = resolveTopic(row, autoCreateCatalog, topicCache);
                scopes.assertCanAuthor(user, path.subjectId());
                QuestionRequest request = ImportRowMapper.toRequest(row, path.topicId());
                Set<ConstraintViolation<QuestionRequest>> violations = beanValidator.validate(request);
                if (!violations.isEmpty()) {
                    throw new IllegalArgumentException(violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .sorted().collect(Collectors.joining("; ")));
                }
                valid.add(questionService.buildNew(request, path));
            } catch (BusinessException | IllegalArgumentException e) {
                if (errors.size() < MAX_REPORTED_ERRORS) {
                    errors.add(new RowError(row.rowNumber(), e.getMessage()));
                }
            }
        }

        if (!errors.isEmpty()) {
            // Undo any auto-created chapters/topics. Setting rollback-only locally keeps it silent.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return new ImportReport(rows.size(), valid.size(), 0, dryRun, errors);
        }

        applyWorkflow(valid, after, user);
        questionRepository.saveAll(valid);
        questionRepository.flush();   // surface constraint violations now, including on a dry run
        versions.recordInitial(valid, user.id(), "Imported", after == AfterImport.PUBLISH);

        if (dryRun) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return new ImportReport(rows.size(), valid.size(), 0, true, List.of());
        }
        log.info("Imported {} questions from {} ({})", valid.size(), file.getOriginalFilename(), after);
        return new ImportReport(rows.size(), valid.size(), valid.size(), false, List.of());
    }

    private void applyWorkflow(List<Question> questions, AfterImport after, AuthUser user) {
        Instant now = clock.instant();
        Duration sla = Duration.ofHours(settings.get().slaHours());
        for (Question q : questions) {
            q.setCurrentVersion(1);
            switch (after) {
                case DRAFT -> q.setStatus(QuestionStatus.DRAFT);
                case SUBMIT -> {
                    q.setStatus(QuestionStatus.IN_REVIEW);
                    q.setSubmittedBy(user.id());
                    q.setReviewRequestedAt(now);
                    q.setReviewDueAt(now.plus(sla));
                }
                case PUBLISH -> {
                    q.setStatus(QuestionStatus.PUBLISHED);
                    q.setPublishedVersion(1);
                    q.setPublishedAt(now);
                }
            }
        }
    }

    private TopicPath resolveTopic(ImportRow row, boolean autoCreate, Map<String, TopicPath> cache) {
        String exam = row.required(ImportColumns.EXAM_CODE);
        String subject = row.required(ImportColumns.SUBJECT_CODE);
        String chapter = row.required(ImportColumns.CHAPTER);
        String topic = row.required(ImportColumns.TOPIC);
        String key = String.join("|", exam, subject, chapter, topic).toLowerCase(Locale.ROOT);

        TopicPath cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        TopicPath path = catalogAdmin.resolveOrCreateTopic(exam, subject, chapter, topic, autoCreate);
        cache.put(key, path);
        return path;
    }
}
