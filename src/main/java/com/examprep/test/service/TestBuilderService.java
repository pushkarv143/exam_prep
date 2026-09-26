package com.examprep.test.service;

import com.examprep.catalog.dto.CatalogTreeDto;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.question.dto.QuestionRef;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.service.QuestionLookupService;
import com.examprep.test.dto.BuilderDtos.AddQuestionsRequest;
import com.examprep.test.dto.BuilderDtos.AddQuestionsResult;
import com.examprep.test.dto.BuilderDtos.ReorderRequest;
import com.examprep.test.dto.BuilderDtos.SectionRequest;
import com.examprep.test.dto.BuilderDtos.UpdateTestQuestionRequest;
import com.examprep.test.dto.TestDtos.SectionDto;
import com.examprep.test.dto.TestDtos.TestDetailDto;
import com.examprep.test.dto.TestDtos.TestQuestionDto;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestQuestion;
import com.examprep.test.entity.TestSection;
import com.examprep.test.entity.TestStatus;
import com.examprep.test.mapper.TestMapper;
import com.examprep.test.repository.TestQuestionRepository;
import com.examprep.test.repository.TestRepository;
import com.examprep.test.repository.TestSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Structural editing of a DRAFT test: sections, question placement, marks and ordering.
 * Every mutation recomputes the denormalised totals on the test.
 */
@Service
@RequiredArgsConstructor
public class TestBuilderService {

    private final TestRepository testRepository;
    private final TestSectionRepository sectionRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final QuestionLookupService questions;
    private final CatalogQueryService catalog;
    private final TestMapper mapper;

    // ------------------------------------------------------------------ sections

    @Transactional
    public SectionDto addSection(UUID testId, SectionRequest req) {
        Test test = requireDraft(testId);
        validateSubject(test, req.subjectId());
        TestSection section = new TestSection();
        section.setTestId(testId);
        applySection(section, req);
        sectionRepository.save(section);
        return toSectionDto(section, List.of(), Map.of());
    }

    @Transactional
    public SectionDto updateSection(UUID testId, UUID sectionId, SectionRequest req) {
        Test test = requireDraft(testId);
        TestSection section = requireSection(testId, sectionId);
        validateSubject(test, req.subjectId());
        applySection(section, req);
        recalculateTotals(test);
        List<TestQuestion> tqs = testQuestionRepository.findBySectionIdOrderByDisplayOrderAsc(sectionId);
        return toSectionDto(section, tqs, questions.findSummaries(questionIds(tqs)));
    }

    @Transactional
    public void deleteSection(UUID testId, UUID sectionId) {
        Test test = requireDraft(testId);
        TestSection section = requireSection(testId, sectionId);
        testQuestionRepository.deleteBySectionId(sectionId);
        sectionRepository.delete(section);
        sectionRepository.flush();
        recalculateTotals(test);
    }

    // ------------------------------------------------------------------ questions

    @Transactional
    public AddQuestionsResult addQuestions(UUID testId, UUID sectionId, AddQuestionsRequest req) {
        Test test = requireDraft(testId);
        TestSection section = requireSection(testId, sectionId);
        List<UUID> skipped = new ArrayList<>();
        List<UUID> expanded = new ArrayList<>();
        int added = append(section, req.questionIds(), req.marks(), req.negativeMarks(), req.partialMarking(),
                new HashSet<>(testQuestionRepository.findQuestionIdsByTestId(testId)), skipped, expanded);
        recalculateTotals(test);
        return new AddQuestionsResult(added, skipped, expanded);
    }

    @Transactional
    public void removeQuestion(UUID testId, UUID testQuestionId) {
        Test test = requireDraft(testId);
        TestQuestion tq = testQuestionRepository.findByIdAndTestId(testQuestionId, testId)
                .orElseThrow(() -> NotFoundException.of("Test question", testQuestionId));
        testQuestionRepository.delete(tq);
        testQuestionRepository.flush();
        renumber(testQuestionRepository.findBySectionIdOrderByDisplayOrderAsc(tq.getSectionId()));
        recalculateTotals(test);
    }

    /** Applies a drag-and-drop order. The request must list exactly the section's questions. */
    @Transactional
    public void reorder(UUID testId, UUID sectionId, ReorderRequest req) {
        requireDraft(testId);
        requireSection(testId, sectionId);
        List<TestQuestion> current = testQuestionRepository.findBySectionIdOrderByDisplayOrderAsc(sectionId);
        Map<UUID, TestQuestion> byId = current.stream().collect(Collectors.toMap(TestQuestion::getId, t -> t));
        if (req.testQuestionIds().size() != current.size()
                || !byId.keySet().equals(new HashSet<>(req.testQuestionIds()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Reorder must list every question of the section exactly once");
        }
        renumber(req.testQuestionIds().stream().map(byId::get).toList());
    }

    @Transactional
    public TestQuestionDto updateQuestion(UUID testId, UUID testQuestionId, UpdateTestQuestionRequest req) {
        Test test = requireDraft(testId);
        TestQuestion tq = testQuestionRepository.findByIdAndTestId(testQuestionId, testId)
                .orElseThrow(() -> NotFoundException.of("Test question", testQuestionId));
        tq.setMarks(req.marks());
        tq.setNegativeMarks(req.negativeMarks());
        tq.setPartialMarking(req.partialMarking());
        recalculateTotals(test);
        return toTestQuestionDto(tq, questions.findSummaries(List.of(tq.getQuestionId())).get(tq.getQuestionId()));
    }

    // ------------------------------------------------------------------ shared with generator / service

    /**
     * Validates and appends questions to a section, preserving request order.
     * Rules: questions must exist and be published (not archived), and are pinned to their
     * published version; PARAGRAPH ids expand to their published children; types must match the section's {@code questionType}; questions already
     * in the test are skipped (reported, not an error). Any hard problem rejects the
     * whole batch.
     *
     * @param inTest question ids already in the test. It is updated with the added ids.
     * @return number of questions added
     */
    int append(TestSection section, List<UUID> requestedIds, BigDecimal marks, BigDecimal negativeMarks,
               Boolean partialMarking, Set<UUID> inTest, List<UUID> skipped, List<UUID> expanded) {
        Map<UUID, QuestionRef> refs = questions.findRefs(requestedIds);
        List<QuestionRef> candidates = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (UUID id : requestedIds) {
            QuestionRef ref = refs.get(id);
            if (ref == null) {
                problems.add("Question not found: " + id);
            } else if (ref.type() == QuestionType.PARAGRAPH) {
                List<UUID> children = questions.findUsableChildIds(id);
                if (children.isEmpty()) {
                    problems.add("Paragraph " + id + " has no published child questions");
                } else {
                    expanded.add(id);
                    Map<UUID, QuestionRef> childRefs = questions.findRefs(children);
                    children.forEach(c -> candidates.add(childRefs.get(c)));
                }
            } else {
                candidates.add(ref);
            }
        }

        List<QuestionRef> toAdd = new ArrayList<>();
        for (QuestionRef ref : candidates) {
            if (!ref.usable()) {
                problems.add("Question " + ref.id() + (ref.publishedVersion() == null
                        ? " has not been published yet; only published questions can be added"
                        : " is archived"));
            } else if (section.getQuestionType() != null && ref.type() != section.getQuestionType()) {
                problems.add("Question " + ref.id() + " is " + ref.type() + " but section '" + section.getName()
                        + "' accepts only " + section.getQuestionType());
            } else if (!inTest.add(ref.id())) {
                skipped.add(ref.id());
            } else {
                toAdd.add(ref);
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, String.join("; ", problems));
        }

        Map<UUID, QuestionRef> parents = questions.findRefs(toAdd.stream().map(QuestionRef::parentId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        int order = testQuestionRepository.maxDisplayOrder(section.getId());
        List<TestQuestion> rows = new ArrayList<>(toAdd.size());
        for (QuestionRef ref : toAdd) {
            TestQuestion tq = new TestQuestion();
            tq.setTestId(section.getTestId());
            tq.setSectionId(section.getId());
            tq.setQuestionId(ref.id());
            tq.setQuestionVersion(ref.publishedVersion());
            tq.setPassageVersion(passageVersion(ref, parents));
            tq.setDisplayOrder(++order);
            tq.setMarks(firstNonNull(marks, section.getDefaultMarks(), ref.defaultMarks()));
            tq.setNegativeMarks(firstNonNull(negativeMarks, section.getDefaultNegativeMarks(),
                    ref.defaultNegativeMarks()));
            tq.setPartialMarking(partialMarking != null ? partialMarking
                    : ref.type() == QuestionType.MULTIPLE_CORRECT);
            rows.add(tq);
        }
        testQuestionRepository.saveAll(rows);
        return rows.size();
    }

    /** The published version of the passage a paragraph child belongs to (its current version if never published). */
    private static Integer passageVersion(QuestionRef ref, Map<UUID, QuestionRef> parents) {
        if (ref.parentId() == null) {
            return null;
        }
        QuestionRef parent = parents.get(ref.parentId());
        if (parent == null) {
            return null;
        }
        return parent.publishedVersion() != null ? parent.publishedVersion() : parent.currentVersion();
    }

    /**
     * Moves every question of a DRAFT test to its latest published version (and passage).
     * Published tests never change here; see the question publish flow for wording fixes.
     *
     * @return number of questions that moved
     */
    @Transactional
    public int updateToLatestVersions(UUID testId) {
        requireDraft(testId);
        List<TestQuestion> tqs = testQuestionRepository.findByTestIdOrderByDisplayOrderAsc(testId);
        Map<UUID, QuestionRef> refs = questions.findRefs(questionIds(tqs));
        Map<UUID, QuestionRef> parents = questions.findRefs(refs.values().stream().map(QuestionRef::parentId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        int moved = 0;
        for (TestQuestion tq : tqs) {
            QuestionRef ref = refs.get(tq.getQuestionId());
            if (ref == null || ref.publishedVersion() == null) {
                continue;
            }
            Integer passage = passageVersion(ref, parents);
            if (ref.publishedVersion() != tq.getQuestionVersion()
                    || !Objects.equals(passage, tq.getPassageVersion())) {
                tq.setQuestionVersion(ref.publishedVersion());
                tq.setPassageVersion(passage);
                moved++;
            }
        }
        return moved;
    }

    /**
     * Recomputes total questions and total marks. With "attempt any N", a section
     * contributes only its N highest-mark questions to the maximum score.
     */
    void recalculateTotals(Test test) {
        testQuestionRepository.flush();
        List<TestSection> sections = sectionRepository.findByTestIdOrderByDisplayOrderAscCreatedAtAsc(test.getId());
        Map<UUID, List<TestQuestion>> bySection = testQuestionRepository
                .findByTestIdOrderByDisplayOrderAsc(test.getId()).stream()
                .collect(Collectors.groupingBy(TestQuestion::getSectionId));
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (TestSection s : sections) {
            List<TestQuestion> tqs = bySection.getOrDefault(s.getId(), List.of());
            total = total.add(sectionMaxMarks(s, tqs));
            count += tqs.size();
        }
        test.setTotalMarks(total);
        test.setTotalQuestions(count);
    }

    static BigDecimal sectionMaxMarks(TestSection section, Collection<TestQuestion> tqs) {
        int counted = section.getMaxQuestionsToAttempt() == null ? tqs.size()
                : Math.min(section.getMaxQuestionsToAttempt(), tqs.size());
        return tqs.stream().map(TestQuestion::getMarks).sorted(Comparator.reverseOrder()).limit(counted)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public TestDetailDto detail(Test test) {
        List<TestSection> sections = sectionRepository.findByTestIdOrderByDisplayOrderAscCreatedAtAsc(test.getId());
        List<TestQuestion> tqs = testQuestionRepository.findByTestIdOrderByDisplayOrderAsc(test.getId());
        Map<UUID, QuestionSummaryDto> summaries = questions.findSummaries(questionIds(tqs));
        Map<UUID, List<TestQuestion>> bySection = tqs.stream().collect(Collectors.groupingBy(TestQuestion::getSectionId));
        return new TestDetailDto(mapper.toDto(test), sections.stream()
                .map(s -> toSectionDto(s, bySection.getOrDefault(s.getId(), List.of()), summaries))
                .toList());
    }

    Test requireDraft(UUID testId) {
        Test test = testRepository.findById(testId).orElseThrow(() -> NotFoundException.of("Test", testId));
        if (test.getStatus() != TestStatus.DRAFT) {
            throw new BusinessException(ErrorCode.TEST_NOT_EDITABLE,
                    "Test is " + test.getStatus() + "; unpublish it (possible only before any attempt) to edit");
        }
        return test;
    }

    TestSection requireSection(UUID testId, UUID sectionId) {
        return sectionRepository.findByIdAndTestId(sectionId, testId)
                .orElseThrow(() -> NotFoundException.of("Section", sectionId));
    }

    void validateSubject(Test test, UUID subjectId) {
        if (subjectId == null) {
            return;
        }
        CatalogTreeDto tree = catalog.getTree(test.getExamId(), true);
        boolean belongs = tree.subjects().stream().anyMatch(s -> s.id().equals(subjectId));
        if (!belongs) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Subject " + subjectId
                    + " does not belong to the test's exam");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void applySection(TestSection s, SectionRequest req) {
        s.setName(req.name().trim());
        s.setSubjectId(req.subjectId());
        s.setInstructions(req.instructions());
        s.setDisplayOrder(req.displayOrder());
        s.setDefaultMarks(req.defaultMarks());
        s.setDefaultNegativeMarks(req.defaultNegativeMarks());
        s.setMaxQuestionsToAttempt(req.maxQuestionsToAttempt());
        s.setQuestionType(req.questionType());
        s.setTargetCount(req.targetCount());
    }

    private static void renumber(List<TestQuestion> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setDisplayOrder(i + 1);
        }
    }

    private static List<UUID> questionIds(List<TestQuestion> tqs) {
        return tqs.stream().map(TestQuestion::getQuestionId).toList();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private SectionDto toSectionDto(TestSection s, List<TestQuestion> tqs, Map<UUID, QuestionSummaryDto> summaries) {
        List<TestQuestionDto> items = tqs.stream()
                .sorted(Comparator.comparingInt(TestQuestion::getDisplayOrder))
                .map(tq -> toTestQuestionDto(tq, summaries.get(tq.getQuestionId())))
                .toList();
        return new SectionDto(s.getId(), s.getSubjectId(), s.getName(), s.getInstructions(), s.getDisplayOrder(),
                s.getDefaultMarks(), s.getDefaultNegativeMarks(), s.getMaxQuestionsToAttempt(), s.getQuestionType(),
                s.getTargetCount(), tqs.size(), sectionMaxMarks(s, tqs), items);
    }

    private static TestQuestionDto toTestQuestionDto(TestQuestion tq, QuestionSummaryDto summary) {
        return new TestQuestionDto(tq.getId(), tq.getQuestionId(), tq.getDisplayOrder(), tq.getMarks(),
                tq.getNegativeMarks(), tq.isPartialMarking(), tq.getQuestionVersion(), tq.getPassageVersion(),
                summary);
    }
}
