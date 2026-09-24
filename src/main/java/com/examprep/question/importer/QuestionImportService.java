package com.examprep.question.importer;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogAdminService;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.question.dto.ImportReport;
import com.examprep.question.dto.ImportReport.RowError;
import com.examprep.question.dto.QuestionRequest;
import com.examprep.question.entity.Question;
import com.examprep.question.repository.QuestionRepository;
import com.examprep.question.service.QuestionService;
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

    @Transactional
    public ImportReport importQuestions(MultipartFile file, boolean dryRun, boolean autoCreateCatalog) {
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

        questionRepository.saveAll(valid);
        questionRepository.flush();   // surface constraint violations now, including on a dry run

        if (dryRun) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return new ImportReport(rows.size(), valid.size(), 0, true, List.of());
        }
        log.info("Imported {} questions from {}", valid.size(), file.getOriginalFilename());
        return new ImportReport(rows.size(), valid.size(), valid.size(), false, List.of());
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
