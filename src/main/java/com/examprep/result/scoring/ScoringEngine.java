package com.examprep.result.scoring;

import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.model.StudentAnswer;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.result.model.ScoreBreakdowns.SectionScore;
import com.examprep.result.model.ScoreBreakdowns.TopicScore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure, deterministic scoring (no I/O). The rules follow JEE/NEET conventions:
 *
 * <table>
 *   <tr><th>Type</th><th>Correct</th><th>Wrong</th><th>Notes</th></tr>
 *   <tr><td>SINGLE_CORRECT</td><td>+marks</td><td>−negative</td><td></td></tr>
 *   <tr><td>MULTIPLE_CORRECT</td><td>+marks if exactly the correct set</td>
 *       <td>−negative if any wrong option is chosen</td>
 *       <td>Correct options chosen but not all: with partial marking, +marks/4 per correct
 *       option chosen (the JEE Advanced +3/+2/+1 scheme for 4 marks). Without it, −negative.</td></tr>
 *   <tr><td>NUMERICAL</td><td>|answer − key| ≤ tolerance (exact if none)</td><td>−negative</td>
 *       <td>Unparseable input counts as wrong.</td></tr>
 *   <tr><td>MATCH</td><td>all pairs right</td><td>−negative</td><td></td></tr>
 * </table>
 *
 * <ul>
 *   <li><b>Unattempted</b> (no answer, including MARKED_FOR_REVIEW without an answer) scores 0.</li>
 *   <li><b>ANSWERED_AND_MARKED is evaluated</b> (NTA rule).</li>
 *   <li><b>"Attempt any N"</b>: within a section, only the first N answered questions
 *       <em>in question order</em> are evaluated. Further answers are ignored (scored as
 *       unattempted, 0).</li>
 *   <li><b>Accuracy</b> = correct / attempted × 100, where attempted = correct + incorrect + partial.</li>
 * </ul>
 */
public final class ScoringEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);

    private ScoringEngine() {
    }

    public enum OutcomeType { CORRECT, INCORRECT, PARTIAL, UNATTEMPTED }

    /** A question as placed in the test, with its effective marking and its key. */
    public record QuestionSpec(UUID questionId, UUID sectionId, int order, QuestionType type, BigDecimal marks,
                               BigDecimal negativeMarks, boolean partialMarking, AnswerKey key, UUID subjectId,
                               UUID chapterId, UUID topicId) {
    }

    public record SectionSpec(UUID sectionId, String name, UUID subjectId, Integer maxQuestionsToAttempt,
                              BigDecimal maxMarks) {
    }

    public record Response(UUID questionId, StudentAnswer answer, AnswerState state, int timeSpentSeconds) {
    }

    public record Outcome(UUID questionId, OutcomeType outcome, BigDecimal marks) {
    }

    public record Evaluation(BigDecimal score, int correct, int incorrect, int partial, int unattempted,
                             BigDecimal accuracy, int timeSpentSeconds, List<Outcome> outcomes,
                             List<SectionScore> sections, List<TopicScore> topics) {
    }

    public static Evaluation evaluate(List<SectionSpec> sections, List<QuestionSpec> questions,
                                      Map<UUID, Response> responses) {
        Map<UUID, List<QuestionSpec>> bySection = questions.stream()
                .sorted(Comparator.comparingInt(QuestionSpec::order))
                .collect(Collectors.groupingBy(QuestionSpec::sectionId, LinkedHashMap::new, Collectors.toList()));

        List<Outcome> outcomes = new ArrayList<>(questions.size());
        List<SectionScore> sectionScores = new ArrayList<>();
        Map<UUID, TopicAcc> topics = new LinkedHashMap<>();
        Acc total = new Acc();

        for (SectionSpec section : sections) {
            Acc acc = new Acc();
            int answeredSoFar = 0;
            for (QuestionSpec q : bySection.getOrDefault(section.sectionId(), List.of())) {
                Response r = responses.get(q.questionId());
                int time = r == null ? 0 : r.timeSpentSeconds();
                boolean hasAnswer = r != null && r.answer() != null && r.state() != null && r.state().hasAnswer();

                Outcome o;
                if (!hasAnswer) {
                    o = new Outcome(q.questionId(), OutcomeType.UNATTEMPTED, BigDecimal.ZERO);
                } else if (section.maxQuestionsToAttempt() != null && answeredSoFar >= section.maxQuestionsToAttempt()) {
                    o = new Outcome(q.questionId(), OutcomeType.UNATTEMPTED, BigDecimal.ZERO);   // beyond "any N"
                } else {
                    answeredSoFar++;
                    o = score(q, r.answer());
                }
                outcomes.add(o);
                acc.add(o, time);
                total.add(o, time);
                if (q.topicId() != null) {
                    topics.computeIfAbsent(q.topicId(), id -> new TopicAcc(q.topicId(), q.chapterId(), q.subjectId()))
                            .add(o, time, q.marks());
                }
            }
            int count = bySection.getOrDefault(section.sectionId(), List.of()).size();
            sectionScores.add(new SectionScore(section.sectionId(), section.name(), section.subjectId(), count,
                    acc.attempted(), acc.correct, acc.incorrect, acc.partial, acc.unattempted, scale(acc.score),
                    scale(section.maxMarks()), acc.time));
        }

        List<TopicScore> topicScores = topics.values().stream().map(TopicAcc::toScore).toList();
        return new Evaluation(scale(total.score), total.correct, total.incorrect, total.partial, total.unattempted,
                accuracy(total.correct, total.attempted()), total.time, outcomes, sectionScores, topicScores);
    }

    /** Scores one answered question. Package-visible for focused unit tests. */
    static Outcome score(QuestionSpec q, StudentAnswer answer) {
        AnswerKey key = q.key();
        BigDecimal negative = q.negativeMarks().negate();
        return switch (q.type()) {
            case SINGLE_CORRECT -> Objects.equals(sorted(answer.options()), key.options())
                    ? correct(q) : new Outcome(q.questionId(), OutcomeType.INCORRECT, negative);
            case MULTIPLE_CORRECT -> multiple(q, answer, negative);
            case NUMERICAL -> numericalMatches(answer.value(), key)
                    ? correct(q) : new Outcome(q.questionId(), OutcomeType.INCORRECT, negative);
            case MATCH -> key.pairs().equals(answer.pairs() == null ? Map.of() : upper(answer.pairs()))
                    ? correct(q) : new Outcome(q.questionId(), OutcomeType.INCORRECT, negative);
            case PARAGRAPH -> new Outcome(q.questionId(), OutcomeType.UNATTEMPTED, BigDecimal.ZERO);
        };
    }

    private static Outcome multiple(QuestionSpec q, StudentAnswer answer, BigDecimal negative) {
        Set<String> chosen = new HashSet<>(sorted(answer.options()));
        Set<String> correct = new HashSet<>(q.key().options());
        if (!correct.containsAll(chosen)) {
            return new Outcome(q.questionId(), OutcomeType.INCORRECT, negative);   // any wrong option
        }
        if (chosen.equals(correct)) {
            return correct(q);
        }
        if (!q.partialMarking()) {
            return new Outcome(q.questionId(), OutcomeType.INCORRECT, negative);
        }
        BigDecimal perOption = q.marks().divide(FOUR, 2, RoundingMode.HALF_UP);
        return new Outcome(q.questionId(), OutcomeType.PARTIAL, perOption.multiply(BigDecimal.valueOf(chosen.size())));
    }

    static boolean numericalMatches(String value, AnswerKey key) {
        if (value == null || key.value() == null) {
            return false;
        }
        BigDecimal given;
        try {
            given = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (key.tolerance() == null) {
            return given.compareTo(key.value()) == 0;
        }
        return given.subtract(key.value()).abs().compareTo(key.tolerance()) <= 0;
    }

    private static Outcome correct(QuestionSpec q) {
        return new Outcome(q.questionId(), OutcomeType.CORRECT, q.marks());
    }

    private static List<String> sorted(List<String> options) {
        return options == null ? List.of() : options.stream().map(String::toUpperCase).distinct().sorted().toList();
    }

    private static Map<String, String> upper(Map<String, String> pairs) {
        return pairs.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toUpperCase(),
                e -> e.getValue().toUpperCase()));
    }

    private static BigDecimal accuracy(int correct, int attempted) {
        return attempted == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(correct).multiply(HUNDRED).divide(BigDecimal.valueOf(attempted), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static class Acc {
        int correct;
        int incorrect;
        int partial;
        int unattempted;
        int time;
        BigDecimal score = BigDecimal.ZERO;

        void add(Outcome o, int t) {
            time += t;
            score = score.add(o.marks());
            switch (o.outcome()) {
                case CORRECT -> correct++;
                case INCORRECT -> incorrect++;
                case PARTIAL -> partial++;
                case UNATTEMPTED -> unattempted++;
            }
        }

        int attempted() {
            return correct + incorrect + partial;
        }
    }

    private static final class TopicAcc extends Acc {
        final UUID topicId;
        final UUID chapterId;
        final UUID subjectId;
        int total;
        BigDecimal max = BigDecimal.ZERO;

        TopicAcc(UUID topicId, UUID chapterId, UUID subjectId) {
            this.topicId = topicId;
            this.chapterId = chapterId;
            this.subjectId = subjectId;
        }

        void add(Outcome o, int t, BigDecimal marks) {
            super.add(o, t);
            total++;
            max = max.add(marks);
        }

        TopicScore toScore() {
            return new TopicScore(topicId, chapterId, subjectId, total, attempted(), correct, incorrect, partial,
                    scale(score), scale(max), time);
        }
    }
}
