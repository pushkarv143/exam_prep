package com.examprep.test.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.security.AuthUser;
import com.examprep.test.dto.TestLookupDtos.QuestionSlot;
import com.examprep.test.dto.TestLookupDtos.SectionSpec;
import com.examprep.test.dto.TestLookupDtos.TestSnapshot;
import com.examprep.test.dto.TestLookupDtos.TestStructure;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestAvailability;
import com.examprep.test.entity.TestSeries;
import com.examprep.test.repository.TestQuestionRepository;
import com.examprep.test.repository.TestRepository;
import com.examprep.test.repository.TestSectionRepository;
import com.examprep.test.repository.TestSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The test module's public read API for the attempt engine and evaluation. Access and
 * availability rules are applied here, so the attempt module never re-implements them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestLookupService {

    private final TestRepository testRepository;
    private final TestSeriesRepository seriesRepository;
    private final TestSectionRepository sectionRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final SeriesAccessService access;

    public TestSnapshot snapshot(UUID testId) {
        return toSnapshot(load(testId));
    }

    /**
     * Gate for starting an attempt. The window is checked against the clock directly
     * (not the scheduler-maintained status), so nobody gets in late even if the
     * scheduler is behind. Staff bypass enrollment, but not the window.
     *
     * @throws NotFoundException            the test is invisible to this user
     * @throws BusinessException            ACCESS_DENIED_NOT_ENROLLED or TEST_NOT_AVAILABLE
     */
    public TestSnapshot requireStartable(AuthUser user, UUID testId, Instant now) {
        Test test = load(testId);
        if (!user.isStaff()) {
            if (!test.getStatus().isVisibleToStudents()) {
                throw NotFoundException.of("Test", testId);
            }
            TestSeries series = test.getSeriesId() == null ? null
                    : seriesRepository.findById(test.getSeriesId()).orElse(null);
            if (series != null && !access.isVisibleTo(user, series)) {
                throw NotFoundException.of("Test", testId);
            }
        }
        if (!access.canAccessTest(user, test)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED_NOT_ENROLLED);
        }
        TestAvailability availability = TestAvailability.of(test, now);
        switch (availability) {
            case OPEN -> { }
            case UPCOMING -> throw new BusinessException(ErrorCode.TEST_NOT_AVAILABLE,
                    "This test opens at " + test.getStartAt());
            case CLOSED -> throw new BusinessException(ErrorCode.TEST_NOT_AVAILABLE, "This test has closed");
            case NOT_PUBLISHED -> throw new BusinessException(ErrorCode.TEST_NOT_AVAILABLE,
                    "This test is not published");
        }
        return toSnapshot(test);
    }

    public TestStructure structure(UUID testId) {
        Test test = load(testId);
        List<SectionSpec> sections = sectionRepository.findByTestIdOrderByDisplayOrderAscCreatedAtAsc(testId).stream()
                .map(s -> new SectionSpec(s.getId(), s.getName(), s.getSubjectId(), s.getInstructions(),
                        s.getDisplayOrder(), s.getMaxQuestionsToAttempt()))
                .toList();
        List<QuestionSlot> slots = testQuestionRepository.findByTestIdOrderByDisplayOrderAsc(testId).stream()
                .map(q -> new QuestionSlot(q.getId(), q.getSectionId(), q.getQuestionId(), q.getDisplayOrder(),
                        q.getMarks(), q.getNegativeMarks(), q.isPartialMarking()))
                .toList();
        return new TestStructure(toSnapshot(test), sections, slots);
    }

    public List<UUID> testIdsContainingQuestion(UUID questionId) {
        return testQuestionRepository.findTestIdsByQuestionId(questionId);
    }

    private Test load(UUID testId) {
        return testRepository.findById(testId).orElseThrow(() -> NotFoundException.of("Test", testId));
    }

    private static TestSnapshot toSnapshot(Test t) {
        return new TestSnapshot(t.getId(), t.getSeriesId(), t.getExamId(), t.getTitle(), t.getStatus(),
                t.getDurationMinutes(), t.getStartAt(), t.getEndAt(), t.getMaxAttempts(), t.isShuffleQuestions(),
                t.isShuffleOptions(), t.isShowResultImmediately(), t.getTotalMarks(), t.getTotalQuestions());
    }
}
