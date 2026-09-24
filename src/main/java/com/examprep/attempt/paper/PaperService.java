package com.examprep.attempt.paper;

import com.examprep.attempt.AttemptProperties;
import com.examprep.attempt.paper.PaperDto.PaperQuestion;
import com.examprep.attempt.paper.PaperDto.PaperSection;
import com.examprep.common.redis.RedisLock;
import com.examprep.question.dto.StudentQuestionView;
import com.examprep.question.dto.StudentQuestionView.PassageView;
import com.examprep.question.service.QuestionLookupService;
import com.examprep.test.dto.TestLookupDtos.QuestionSlot;
import com.examprep.test.dto.TestLookupDtos.SectionSpec;
import com.examprep.test.dto.TestLookupDtos.TestStructure;
import com.examprep.test.service.TestLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds and caches test papers.
 *
 * <pre>
 *  request ──▶ L1 (this JVM, 60 s) ──▶ L2 Redis "paper:{testId}" (6 h) ──▶ build from Postgres
 *                                                     ▲
 *                               build lock "lock:paper-build:{testId}": one instance builds;
 *                               the others poll L2 briefly instead of stampeding the DB
 * </pre>
 * At 10:00:00 when 50k students press Start, the paper has normally been pre-warmed
 * already (publish / window-open events). Otherwise the lock collapses the misses into one build.
 */
@Slf4j
@Service
public class PaperService {

    private static final String KEY = "paper:";
    private static final String LOCK = "lock:paper-build:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TestLookupService tests;
    private final QuestionLookupService questions;
    private final RedisLock lock;
    private final AttemptProperties props;
    private final MeterRegistry meters;
    private final Map<UUID, Cached> local = new ConcurrentHashMap<>();

    public PaperService(StringRedisTemplate redis, ObjectMapper objectMapper, TestLookupService tests,
                        QuestionLookupService questions, RedisLock lock, AttemptProperties props, MeterRegistry meters) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.tests = tests;
        this.questions = questions;
        this.lock = lock;
        this.props = props;
        this.meters = meters;
    }

    private record Cached(PaperDto paper, PaperIndex index, long expiresAtNanos) {
    }

    public PaperDto paper(UUID testId) {
        return cached(testId).paper();
    }

    public PaperIndex index(UUID testId) {
        return cached(testId).index();
    }

    /** Drops this instance's copy and the shared one. Other instances' L1 copies expire within {@code paperLocalTtl}. */
    public void evict(UUID testId) {
        local.remove(testId);
        try {
            redis.delete(KEY + testId);
        } catch (RuntimeException e) {
            log.warn("Paper cache evict failed for {}: {}", testId, e.getMessage());
        }
    }

    public void warm(UUID testId) {
        evict(testId);
        cached(testId);
        log.info("Paper for test {} pre-warmed", testId);
    }

    // ------------------------------------------------------------------ cache levels

    private Cached cached(UUID testId) {
        Cached c = local.get(testId);
        if (c != null && System.nanoTime() < c.expiresAtNanos()) {
            return c;
        }
        PaperDto paper = readShared(testId).orElseGet(() -> buildWithLock(testId));
        Cached fresh = new Cached(paper, PaperIndex.of(paper), System.nanoTime() + props.paperLocalTtl().toNanos());
        local.put(testId, fresh);
        return fresh;
    }

    private PaperDto buildWithLock(UUID testId) {
        for (int i = 0; i < 30; i++) {
            Optional<RedisLock.Handle> handle;
            try {
                handle = lock.tryAcquire(LOCK + testId, Duration.ofSeconds(10));
            } catch (RuntimeException e) {
                // Redis unavailable: coordination is impossible, so build locally rather than fail the student.
                log.warn("Paper build lock unavailable ({}); building {} directly", e.getMessage(), testId);
                return build(testId);
            }
            if (handle.isPresent()) {
                try (RedisLock.Handle ignored = handle.get()) {
                    Optional<PaperDto> built = readShared(testId);     // another instance may have just finished
                    if (built.isPresent()) {
                        return built.get();
                    }
                    PaperDto paper = build(testId);
                    writeShared(paper);
                    return paper;
                }
            }
            sleep(Duration.ofMillis(100));
            Optional<PaperDto> built = readShared(testId);
            if (built.isPresent()) {
                return built.get();
            }
        }
        log.warn("Paper build lock for {} not obtained in 3s; building without it", testId);
        return build(testId);
    }

    private Optional<PaperDto> readShared(UUID testId) {
        try {
            String json = redis.opsForValue().get(KEY + testId);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, PaperDto.class));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Paper cache read failed for {}: {}", testId, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeShared(PaperDto paper) {
        try {
            redis.opsForValue().set(KEY + paper.testId(), objectMapper.writeValueAsString(paper), props.paperCacheTtl());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Paper cache write failed for {}: {}", paper.testId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ build

    PaperDto build(UUID testId) {
        long start = System.nanoTime();
        TestStructure structure = tests.structure(testId);
        List<UUID> questionIds = structure.slots().stream().map(QuestionSlot::questionId).toList();
        Map<UUID, StudentQuestionView> views = questions.findStudentViews(questionIds);
        Map<UUID, PassageView> passages = questions.findPassages(views.values().stream()
                .map(StudentQuestionView::parentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, List<QuestionSlot>> bySection = structure.slots().stream()
                .collect(Collectors.groupingBy(QuestionSlot::sectionId));

        int number = 0;
        List<PaperSection> sections = new ArrayList<>();
        for (SectionSpec s : structure.sections()) {
            List<PaperQuestion> qs = new ArrayList<>();
            for (QuestionSlot slot : bySection.getOrDefault(s.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(QuestionSlot::displayOrder)).toList()) {
                StudentQuestionView v = views.get(slot.questionId());
                if (v == null) {
                    continue;   // question deleted from the bank; publish validation prevents this normally
                }
                qs.add(new PaperQuestion(v.id(), ++number, v.type(), slot.marks(), slot.negativeMarks(),
                        slot.partialMarking(), v.parentId(), v.text(), v.images(), v.options(), v.matchLeft(),
                        v.matchRight()));
            }
            sections.add(new PaperSection(s.id(), s.name(), s.subjectId(), s.instructions(), s.maxQuestionsToAttempt(),
                    List.copyOf(qs)));
        }
        PaperDto paper = new PaperDto(testId, structure.test().title(), structure.test().durationMinutes(),
                structure.test().totalMarks(), number, List.copyOf(sections), Map.copyOf(passages));
        meters.timer("examprep.paper.build").record(Duration.ofNanos(System.nanoTime() - start));
        log.info("Built paper for test {} ({} questions) in {} ms", testId, number,
                (System.nanoTime() - start) / 1_000_000);
        return paper;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
