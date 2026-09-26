package com.examprep.test.service;

import com.examprep.approval.ApprovalSpec;
import com.examprep.approval.MakerChecker;
import com.examprep.audit.service.AuditContext;
import com.examprep.catalog.dto.CatalogTreeDto;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.question.service.QuestionLookupService;
import com.examprep.test.dto.TestDtos.CreateTestRequest;
import com.examprep.test.dto.TestDtos.PatternDto;
import com.examprep.test.dto.TestDtos.TestDetailDto;
import com.examprep.test.dto.TestDtos.TestDto;
import com.examprep.test.dto.TestDtos.UpdateTestRequest;
import com.examprep.test.dto.TestDtos.ValidationReport;
import com.examprep.test.entity.ExamPattern;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestQuestion;
import com.examprep.test.entity.TestSection;
import com.examprep.test.entity.TestSeries;
import com.examprep.test.entity.TestStatus;
import com.examprep.test.mapper.TestMapper;
import com.examprep.test.repository.TestQuestionRepository;
import com.examprep.test.repository.TestRepository;
import com.examprep.test.repository.TestSectionRepository;
import com.examprep.test.repository.TestSeriesRepository;
import com.examprep.test.event.TestPaperChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Test settings and lifecycle: create (optionally from a pattern), update, validate, publish, archive. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;
    private final TestSeriesRepository seriesRepository;
    private final TestSectionRepository sectionRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final TestBuilderService builder;
    private final TestValidator validator;
    private final QuestionLookupService questions;
    private final MakerChecker makerChecker;
    private final CatalogQueryService catalog;
    private final TestMapper mapper;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Transactional
    public TestDetailDto create(CreateTestRequest req) {
        validateSeries(req.seriesId(), req.examId());
        validateWindow(req.startAt(), req.endAt());
        ExamPattern pattern = req.pattern();
        Integer duration = req.durationMinutes() != null ? req.durationMinutes() : pattern.getDefaultDurationMinutes();
        if (duration == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "durationMinutes is required for CUSTOM tests");
        }

        Test test = new Test();
        test.setSeriesId(req.seriesId());
        test.setExamId(req.examId());
        test.setTitle(req.title().trim());
        test.setDescription(req.description());
        test.setInstructions(req.instructions() != null ? req.instructions() : defaultInstructions(pattern, duration));
        test.setPattern(pattern);
        test.setDurationMinutes(duration);
        test.setStartAt(req.startAt());
        test.setEndAt(req.endAt());
        test.setFree(req.free());
        test.setShuffleQuestions(req.shuffleQuestions());
        test.setShuffleOptions(req.shuffleOptions());
        test.setMaxAttempts(req.maxAttempts() != null ? req.maxAttempts() : 1);
        test.setShowResultImmediately(req.showResultImmediately() == null || req.showResultImmediately());
        test.setDisplayOrder(req.displayOrder());
        testRepository.save(test);

        if (pattern != ExamPattern.CUSTOM) {
            createPatternSections(test, pattern);
        }
        builder.recalculateTotals(test);
        return builder.detail(test);
    }

    @Transactional
    public TestDetailDto update(UUID id, UpdateTestRequest req) {
        Test test = load(id);
        validateSeries(req.seriesId(), test.getExamId());
        validateWindow(req.startAt(), req.endAt());

        if (test.getStatus() != TestStatus.DRAFT) {
            boolean lockedChanged = !Objects.equals(req.seriesId(), test.getSeriesId())
                    || req.durationMinutes() != test.getDurationMinutes()
                    || !Objects.equals(req.startAt(), test.getStartAt())
                    || req.shuffleQuestions() != test.isShuffleQuestions()
                    || req.shuffleOptions() != test.isShuffleOptions()
                    || req.maxAttempts() != test.getMaxAttempts()
                    || (test.getEndAt() != null && (req.endAt() == null || req.endAt().isBefore(test.getEndAt())));
            if (lockedChanged) {
                throw new BusinessException(ErrorCode.TEST_NOT_EDITABLE, "A published test only allows changes to "
                        + "title, description, instructions, free flag, display order, result visibility, or "
                        + "extending the end time");
            }
        }
        test.setSeriesId(req.seriesId());
        test.setTitle(req.title().trim());
        test.setDescription(req.description());
        test.setInstructions(req.instructions());
        test.setDurationMinutes(req.durationMinutes());
        test.setStartAt(req.startAt());
        test.setEndAt(req.endAt());
        test.setFree(req.free());
        test.setShuffleQuestions(req.shuffleQuestions());
        test.setShuffleOptions(req.shuffleOptions());
        test.setMaxAttempts(req.maxAttempts());
        test.setShowResultImmediately(req.showResultImmediately());
        test.setDisplayOrder(req.displayOrder());
        if (test.getStatus() != TestStatus.DRAFT) {
            events.publishEvent(new TestPaperChangedEvent(id, false));   // e.g. title or instructions shown in the paper
        }
        return builder.detail(test);
    }

    @Transactional(readOnly = true)
    public TestDetailDto get(UUID id) {
        return builder.detail(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TestDto> search(UUID seriesId, UUID examId, TestStatus status, String q, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(testRepository.search(seriesId, examId, status, query, pageable), mapper::toDto);
    }

    /** Hard-deletes a DRAFT test that was never attempted (sections/questions cascade in the DB). */
    @Transactional
    public void delete(UUID id) {
        Test test = load(id);
        if (test.getStatus() != TestStatus.DRAFT || testRepository.countAttempts(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only never-attempted DRAFT tests can be deleted; archive it");
        }
        testRepository.delete(test);
    }

    @Transactional(readOnly = true)
    public ValidationReport validate(UUID id) {
        return validate(load(id));
    }

    @Transactional
    public TestDto publish(UUID id) {
        Test test = load(id);
        if (test.getStatus() != TestStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "Test is already " + test.getStatus());
        }
        ValidationReport report = validate(test);
        if (!report.publishable()) {
            throw new BusinessException(ErrorCode.TEST_NOT_PUBLISHABLE, String.join("; ", report.errors()));
        }
        // Maker-checker: validated first, so nobody is asked to approve a paper that cannot be published.
        makerChecker.guard(ApprovalSpec.of("test.publish", "TEST", id, "Publish test \"" + test.getTitle() + "\"",
                Map.of("testId", id, "title", test.getTitle())));
        TestDto before = mapper.toDto(test);
        Instant now = Instant.now(clock);
        boolean windowOpen = test.getStartAt() != null && !test.getStartAt().isAfter(now);
        test.setStatus(windowOpen ? TestStatus.LIVE : TestStatus.PUBLISHED);
        events.publishEvent(new TestPaperChangedEvent(id, test.getStatus() == TestStatus.LIVE || test.getStartAt() == null));
        log.info("Test {} published ({} questions, {} marks)", id, test.getTotalQuestions(), test.getTotalMarks());
        TestDto after = mapper.toDto(test);
        AuditContext.action("test.publish");
        AuditContext.entity("TEST", id);
        AuditContext.change(before, after);
        return after;
    }

    /** Back to DRAFT for further editing, allowed only while nobody has attempted it. */
    @Transactional
    public TestDto unpublish(UUID id) {
        Test test = load(id);
        if (test.getStatus() != TestStatus.PUBLISHED && test.getStatus() != TestStatus.LIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only PUBLISHED or LIVE tests can be unpublished");
        }
        if (testRepository.countAttempts(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Students have already attempted this test; "
                    + "archive it and publish a corrected copy instead");
        }
        test.setStatus(TestStatus.DRAFT);
        events.publishEvent(new TestPaperChangedEvent(id, false));
        return mapper.toDto(test);
    }

    @Transactional
    public TestDto archive(UUID id) {
        Test test = load(id);
        test.setStatus(TestStatus.ARCHIVED);
        events.publishEvent(new TestPaperChangedEvent(id, false));
        return mapper.toDto(test);
    }

    @Transactional(readOnly = true)
    public String title(UUID id) {
        return load(id).getTitle();
    }

    /** Called by the result module once final ranks and percentiles are persisted. */
    @Transactional
    public void markRanksComputed(UUID id, Instant at) {
        load(id).setRanksComputedAt(at);
    }

    public List<PatternDto> patterns() {
        return Arrays.stream(ExamPattern.values())
                .map(p -> new PatternDto(p, p.getDisplayName(), p.getDefaultDurationMinutes(), p.totalQuestions(),
                        p.totalMarks(), p.getSections()))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private Test load(UUID id) {
        return testRepository.findById(id).orElseThrow(() -> NotFoundException.of("Test", id));
    }

    private ValidationReport validate(Test test) {
        List<TestSection> sections = sectionRepository.findByTestIdOrderByDisplayOrderAscCreatedAtAsc(test.getId());
        List<TestQuestion> tqs = testQuestionRepository.findByTestIdOrderByDisplayOrderAsc(test.getId());
        TestSeries series = test.getSeriesId() == null ? null
                : seriesRepository.findById(test.getSeriesId()).orElse(null);
        return validator.validate(test, series, sections, tqs,
                questions.findRefs(tqs.stream().map(TestQuestion::getQuestionId).toList()), Instant.now(clock));
    }

    private void createPatternSections(Test test, ExamPattern pattern) {
        CatalogTreeDto tree = catalog.getTree(test.getExamId(), false);
        Map<String, UUID> subjectByCode = tree.subjects().stream()
                .collect(Collectors.toMap(CatalogTreeDto.SubjectNode::code, CatalogTreeDto.SubjectNode::id));
        int order = 0;
        for (ExamPattern.SectionTemplate t : pattern.getSections()) {
            UUID subjectId = subjectByCode.get(t.subjectCode());
            if (subjectId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Pattern " + pattern + " needs subject code "
                        + t.subjectCode() + ", which exam " + tree.exam().code() + " does not have");
            }
            TestSection s = new TestSection();
            s.setTestId(test.getId());
            s.setSubjectId(subjectId);
            s.setName(t.name());
            s.setDisplayOrder(++order);
            s.setDefaultMarks(t.marks());
            s.setDefaultNegativeMarks(t.negativeMarks());
            s.setMaxQuestionsToAttempt(t.maxAttempt());
            s.setQuestionType(t.type());
            s.setTargetCount(t.count());
            sectionRepository.save(s);
        }
    }

    private void validateSeries(UUID seriesId, UUID examId) {
        if (seriesId == null) {
            return;
        }
        TestSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Unknown series: " + seriesId));
        if (!series.getExamId().equals(examId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Test exam must match the series exam");
        }
    }

    private static void validateWindow(Instant start, Instant end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endAt must be after startAt");
        }
    }

    private static String defaultInstructions(ExamPattern pattern, int duration) {
        StringBuilder sb = new StringBuilder();
        sb.append("1. The test duration is ").append(duration).append(" minutes. The timer is controlled by the "
                + "server, and the test is submitted automatically when time runs out.\n");
        sb.append("2. Use the question palette to navigate. Colours show: not visited, not answered, answered, "
                + "marked for review, and answered & marked for review.\n");
        int i = 3;
        for (ExamPattern.SectionTemplate s : pattern.getSections()) {
            if (s.maxAttempt() != null) {
                sb.append(i++).append(". ").append(s.name()).append(": attempt any ").append(s.maxAttempt())
                        .append(" of ").append(s.count()).append(".\n");
            }
        }
        sb.append(i).append(". Marking: correct answers earn the marks shown for each question; wrong answers "
                + "may carry negative marks; unanswered questions score 0.");
        return sb.toString();
    }
}
