package com.examprep.result.service;

import com.examprep.question.dto.QuestionPin;
import com.examprep.question.dto.ScoringRef;
import com.examprep.question.service.QuestionLookupService;
import com.examprep.result.EvaluationProperties;
import com.examprep.result.scoring.ScoringEngine.QuestionSpec;
import com.examprep.result.scoring.ScoringEngine.SectionSpec;
import com.examprep.test.dto.TestLookupDtos.QuestionSlot;
import com.examprep.test.dto.TestLookupDtos.TestSnapshot;
import com.examprep.test.dto.TestLookupDtos.TestStructure;
import com.examprep.test.service.TestLookupService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Per-test scoring specification (structure, marking and answer keys), cached in memory.
 * Structure and pinned versions are fixed once a test is published (a same-scoring wording
 * fix evicts the entry), so a short TTL is only a safety net. Evaluating 50k attempts of one test costs one spec build per instance,
 * not 50k key lookups.
 */
@Component
public class EvaluationSpecCache {

    private final TestLookupService tests;
    private final QuestionLookupService questions;
    private final EvaluationProperties props;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public EvaluationSpecCache(TestLookupService tests, QuestionLookupService questions, EvaluationProperties props) {
        this.tests = tests;
        this.questions = questions;
        this.props = props;
    }

    /** @param pins question id to the pinned version (and passage version), from the test structure */
    public record Spec(TestSnapshot test, List<SectionSpec> sections, List<QuestionSpec> questions,
                       Map<UUID, QuestionSlot> pins) {
    }

    private record Cached(Spec spec, long expiresAtNanos) {
    }

    public Spec get(UUID testId) {
        Cached c = cache.get(testId);
        if (c != null && System.nanoTime() < c.expiresAtNanos()) {
            return c.spec();
        }
        Spec spec = build(testId);
        cache.put(testId, new Cached(spec, System.nanoTime() + props.specCacheTtl().toNanos()));
        return spec;
    }

    public void evict(UUID testId) {
        cache.remove(testId);
    }

    private Spec build(UUID testId) {
        TestStructure structure = tests.structure(testId);
        Map<UUID, ScoringRef> refs = questions.findScoringRefs(structure.slots().stream()
                .map(s -> new QuestionPin(s.questionId(), s.questionVersion())).toList());
        List<QuestionSpec> qs = structure.slots().stream()
                .filter(s -> refs.containsKey(s.questionId()))
                .map(s -> {
                    ScoringRef r = refs.get(s.questionId());
                    return new QuestionSpec(s.questionId(), s.sectionId(), s.displayOrder(), r.type(), s.marks(),
                            s.negativeMarks(), s.partialMarking(), r.answerKey(), r.subjectId(), r.chapterId(),
                            r.topicId());
                })
                .toList();
        List<SectionSpec> sections = structure.sections().stream()
                .map(sec -> new SectionSpec(sec.id(), sec.name(), sec.subjectId(), sec.maxQuestionsToAttempt(),
                        maxMarks(qs, sec.id(), sec.maxQuestionsToAttempt())))
                .toList();
        return new Spec(structure.test(), sections, qs, structure.slots().stream()
                .collect(Collectors.toMap(QuestionSlot::questionId, s -> s, (a, b) -> a)));
    }

    /** Same rule as the test builder: with "attempt any N", only the N highest marks count toward the maximum. */
    private static BigDecimal maxMarks(List<QuestionSpec> qs, UUID sectionId, Integer limit) {
        List<BigDecimal> marks = qs.stream().filter(q -> q.sectionId().equals(sectionId)).map(QuestionSpec::marks)
                .sorted(Comparator.reverseOrder()).toList();
        return marks.stream().limit(limit == null ? marks.size() : limit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
