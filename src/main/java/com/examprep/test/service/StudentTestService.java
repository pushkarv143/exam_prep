package com.examprep.test.service;

import com.examprep.common.exception.NotFoundException;
import com.examprep.security.AuthUser;
import com.examprep.test.dto.TestDtos.SectionInfo;
import com.examprep.test.dto.TestDtos.TestInfoDto;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestAvailability;
import com.examprep.test.entity.TestQuestion;
import com.examprep.test.entity.TestSection;
import com.examprep.test.entity.TestSeries;
import com.examprep.test.repository.TestQuestionRepository;
import com.examprep.test.repository.TestRepository;
import com.examprep.test.repository.TestSectionRepository;
import com.examprep.test.repository.TestSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Student-facing test info: the instructions page shown before starting an attempt. */
@Service
@RequiredArgsConstructor
public class StudentTestService {

    private final TestRepository testRepository;
    private final TestSeriesRepository seriesRepository;
    private final TestSectionRepository sectionRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final SeriesAccessService access;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TestInfoDto getInfo(AuthUser user, UUID testId) {
        Test test = testRepository.findById(testId)
                .filter(t -> user.isStaff() || t.getStatus().isVisibleToStudents())
                .orElseThrow(() -> NotFoundException.of("Test", testId));
        TestSeries series = test.getSeriesId() == null ? null
                : seriesRepository.findById(test.getSeriesId()).orElse(null);
        if (series != null && !access.isVisibleTo(user, series)) {
            throw NotFoundException.of("Test", testId);
        }

        List<TestSection> sections = sectionRepository.findByTestIdOrderByDisplayOrderAscCreatedAtAsc(testId);
        Map<UUID, List<TestQuestion>> bySection = testQuestionRepository.findByTestIdOrderByDisplayOrderAsc(testId)
                .stream().collect(Collectors.groupingBy(TestQuestion::getSectionId));
        List<SectionInfo> info = sections.stream().map(s -> {
            List<TestQuestion> tqs = bySection.getOrDefault(s.getId(), List.of());
            return new SectionInfo(s.getName(), s.getSubjectId(), tqs.size(), s.getMaxQuestionsToAttempt(),
                    TestBuilderService.sectionMaxMarks(s, tqs));
        }).toList();

        Instant now = Instant.now(clock);
        return new TestInfoDto(test.getId(), test.getSeriesId(), series == null ? null : series.getSlug(),
                test.getTitle(), test.getDescription(), test.getInstructions(), test.getPattern(),
                test.getDurationMinutes(), test.getTotalMarks(), test.getTotalQuestions(), test.getStartAt(),
                test.getEndAt(), TestAvailability.of(test, now), test.isFree(), test.getMaxAttempts(),
                access.canAccessTest(user, test), info);
    }
}
