package com.examprep.test.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.question.dto.PickCriteria;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.service.QuestionLookupService;
import com.examprep.test.dto.BuilderDtos.GenerateFromPatternRequest;
import com.examprep.test.dto.BuilderDtos.GenerateRequest;
import com.examprep.test.dto.BuilderDtos.GenerationReport;
import com.examprep.test.dto.BuilderDtos.GenerationRule;
import com.examprep.test.dto.BuilderDtos.Shortfall;
import com.examprep.test.entity.ExamPattern;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestQuestion;
import com.examprep.test.entity.TestSection;
import com.examprep.test.repository.TestQuestionRepository;
import com.examprep.test.repository.TestSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fills test sections with random questions from the bank.
 *
 * <ul>
 *   <li>Picks never duplicate questions already in the test, or picked earlier in the same run.</li>
 *   <li>Each section's subject (and its {@code questionType}, if set) always constrains the pool.</li>
 *   <li>{@code strict = true}: if any rule comes up short, the transaction rolls back
 *       and nothing is added.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TestGeneratorService {

    private final TestBuilderService builder;
    private final TestSectionRepository sectionRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final QuestionLookupService questions;

    @Transactional
    public GenerationReport generate(UUID testId, GenerateRequest req) {
        Test test = builder.requireDraft(testId);
        Set<UUID> inTest = new HashSet<>(testQuestionRepository.findQuestionIdsByTestId(testId));
        List<Shortfall> shortfalls = new ArrayList<>();
        int added = 0;

        for (GenerationRule rule : req.rules()) {
            TestSection section = builder.requireSection(testId, rule.sectionId());
            PickCriteria criteria = new PickCriteria(section.getSubjectId(), rule.chapterIds(), rule.topicIds(),
                    rule.difficulty(), typesFor(section, rule.types()), rule.language(),
                    req.excludeUsedInPublishedTests());
            added += pickInto(section, criteria, rule.count(), inTest, describe(rule), shortfalls);
        }
        return finish(test, added, shortfalls, req.strict());
    }

    /**
     * Tops up every pattern section to its target count, using the requested difficulty
     * mix. If a difficulty runs dry, the remainder is filled from any difficulty before
     * a shortfall is reported.
     */
    @Transactional
    public GenerationReport generateFromPattern(UUID testId, GenerateFromPatternRequest req) {
        if (req.easyPercent() + req.mediumPercent() + req.hardPercent() != 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Difficulty percentages must add up to 100");
        }
        Test test = builder.requireDraft(testId);
        if (test.getPattern() == ExamPattern.CUSTOM) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CUSTOM tests have no targets; use /generate with rules");
        }
        Set<UUID> inTest = new HashSet<>(testQuestionRepository.findQuestionIdsByTestId(testId));
        Map<UUID, Long> countBySection = testQuestionRepository.findByTestIdOrderByDisplayOrderAsc(testId).stream()
                .collect(Collectors.groupingBy(TestQuestion::getSectionId, Collectors.counting()));

        List<Shortfall> shortfalls = new ArrayList<>();
        int added = 0;
        for (TestSection section : sectionRepository.findByTestIdOrderByDisplayOrderAscCreatedAtAsc(testId)) {
            if (section.getTargetCount() == null) {
                continue;
            }
            int missing = section.getTargetCount() - countBySection.getOrDefault(section.getId(), 0L).intValue();
            if (missing <= 0) {
                continue;
            }
            int got = 0;
            for (Map.Entry<Difficulty, Integer> share : split(missing, req).entrySet()) {
                got += pickInto(section, criteria(section, share.getKey(), req.language(),
                        req.excludeUsedInPublishedTests()), share.getValue(), inTest, null, null);
            }
            if (got < missing) {
                got += pickInto(section, criteria(section, null, req.language(), req.excludeUsedInPublishedTests()),
                        missing - got, inTest, null, null);
            }
            if (got < missing) {
                shortfalls.add(new Shortfall(section.getId(), section.getName(), "fill to target", missing, got));
            }
            added += got;
        }
        return finish(test, added, shortfalls, req.strict());
    }

    // ------------------------------------------------------------------ helpers

    private int pickInto(TestSection section, PickCriteria criteria, int count, Set<UUID> inTest, String ruleLabel,
                         List<Shortfall> shortfalls) {
        if (count <= 0) {
            return 0;
        }
        List<UUID> picked = questions.pickRandom(criteria, count, inTest);
        if (shortfalls != null && picked.size() < count) {
            shortfalls.add(new Shortfall(section.getId(), section.getName(), ruleLabel, count, picked.size()));
        }
        if (picked.isEmpty()) {
            return 0;
        }
        return builder.append(section, picked, null, null, null, inTest, new ArrayList<>(), new ArrayList<>());
    }

    private GenerationReport finish(Test test, int added, List<Shortfall> shortfalls, boolean strict) {
        if (strict && !shortfalls.isEmpty()) {
            String detail = shortfalls.stream()
                    .map(s -> s.sectionName() + ": found " + s.found() + " of " + s.requested())
                    .collect(Collectors.joining("; "));
            // Throwing rolls back every insert made above.
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Not enough questions in the bank: " + detail);
        }
        builder.recalculateTotals(test);
        return new GenerationReport(added, shortfalls);
    }

    private static PickCriteria criteria(TestSection section, Difficulty difficulty, Language language,
                                         boolean excludeUsed) {
        return new PickCriteria(section.getSubjectId(), null, null, difficulty, typesFor(section, null), language,
                excludeUsed);
    }

    /** The section's type constraint always wins over the rule's; an unconstrained section accepts all answerable types. */
    private static Set<QuestionType> typesFor(TestSection section, Set<QuestionType> requested) {
        if (section.getQuestionType() != null) {
            return Set.of(section.getQuestionType());
        }
        return requested;
    }

    /** Largest-remainder split of {@code n} by the percentages, so the parts always sum to {@code n}. */
    static Map<Difficulty, Integer> split(int n, GenerateFromPatternRequest req) {
        int[] pct = {req.easyPercent(), req.mediumPercent(), req.hardPercent()};
        Difficulty[] levels = {Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD};
        int[] parts = new int[3];
        double[] remainders = new double[3];
        int assigned = 0;
        for (int i = 0; i < 3; i++) {
            double exact = n * pct[i] / 100.0;
            parts[i] = (int) Math.floor(exact);
            remainders[i] = exact - parts[i];
            assigned += parts[i];
        }
        while (assigned < n) {
            int best = 0;
            for (int i = 1; i < 3; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            parts[best]++;
            remainders[best] = -1;
            assigned++;
        }
        Map<Difficulty, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            result.put(levels[i], parts[i]);
        }
        return result;
    }

    private static String describe(GenerationRule r) {
        StringBuilder sb = new StringBuilder(r.count() + " questions");
        if (r.difficulty() != null) {
            sb.append(", ").append(r.difficulty());
        }
        if (r.topicIds() != null && !r.topicIds().isEmpty()) {
            sb.append(", ").append(r.topicIds().size()).append(" topic(s)");
        }
        if (r.chapterIds() != null && !r.chapterIds().isEmpty()) {
            sb.append(", ").append(r.chapterIds().size()).append(" chapter(s)");
        }
        return sb.toString();
    }
}
