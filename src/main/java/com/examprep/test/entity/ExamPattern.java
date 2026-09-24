package com.examprep.test.entity;

import com.examprep.question.entity.QuestionType;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pre-built paper patterns. Creating a test with a pattern creates its sections (per
 * subject code of the test's exam) with marking rules and target counts. The
 * auto-generator can then fill the whole paper in one call.
 *
 * <p>These are data, not logic. When NTA changes a pattern, edit the templates here.
 * Existing tests are unaffected, because a test copies the rules into its own sections
 * when it is created.
 *
 * <ul>
 *   <li><b>JEE_MAIN</b> — 90 Q / 300 marks / 180 min. Per subject: Section A has 20 MCQ
 *       (+4/−1); Section B has 10 numerical, attempt any 5 (+4/0).</li>
 *   <li><b>NEET</b> — 180 Q / 720 marks / 200 min. 45 MCQ each in Physics, Chemistry,
 *       Botany and Zoology (+4/−1).</li>
 *   <li><b>JEE_ADVANCED</b> — an illustrative one-paper layout (the real pattern changes
 *       every year). Per subject: 4 single-correct (+3/−1), 3 multi-correct with partial
 *       marking (+4/−2), 6 numerical (+4/0).</li>
 *   <li><b>CUSTOM</b> — no sections; build freely.</li>
 * </ul>
 */
@Getter
public enum ExamPattern {

    JEE_MAIN("JEE Main (NTA)", 180, jeeMainSections()),
    NEET("NEET UG", 200, List.of(
            section("PHY", "Physics", QuestionType.SINGLE_CORRECT, 45, 4, 1, null),
            section("CHEM", "Chemistry", QuestionType.SINGLE_CORRECT, 45, 4, 1, null),
            section("BOT", "Botany", QuestionType.SINGLE_CORRECT, 45, 4, 1, null),
            section("ZOO", "Zoology", QuestionType.SINGLE_CORRECT, 45, 4, 1, null))),
    JEE_ADVANCED("JEE Advanced (single paper)", 180, jeeAdvancedSections()),
    CUSTOM("Custom", null, List.of());

    private final String displayName;
    /** Null for CUSTOM: the duration must then be given explicitly. */
    private final Integer defaultDurationMinutes;
    private final List<SectionTemplate> sections;

    ExamPattern(String displayName, Integer defaultDurationMinutes, List<SectionTemplate> sections) {
        this.displayName = displayName;
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.sections = sections;
    }

    public int totalQuestions() {
        return sections.stream().mapToInt(SectionTemplate::count).sum();
    }

    public BigDecimal totalMarks() {
        return sections.stream()
                .map(s -> s.marks().multiply(BigDecimal.valueOf(s.maxAttempt() != null ? s.maxAttempt() : s.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @param subjectCode subject code within the test's exam (PHY, CHEM, MATH, BOT, ZOO)
     * @param maxAttempt  "attempt any N" limit, or null if all questions count
     */
    public record SectionTemplate(String subjectCode, String name, QuestionType type, int count,
                                  BigDecimal marks, BigDecimal negativeMarks, Integer maxAttempt) {
    }

    private static SectionTemplate section(String subject, String name, QuestionType type, int count, int marks,
                                           int negative, Integer maxAttempt) {
        return new SectionTemplate(subject, name, type, count, BigDecimal.valueOf(marks),
                BigDecimal.valueOf(negative), maxAttempt);
    }

    private static List<SectionTemplate> jeeMainSections() {
        return List.of(
                section("PHY", "Physics - Section A (MCQ)", QuestionType.SINGLE_CORRECT, 20, 4, 1, null),
                section("PHY", "Physics - Section B (Numerical)", QuestionType.NUMERICAL, 10, 4, 0, 5),
                section("CHEM", "Chemistry - Section A (MCQ)", QuestionType.SINGLE_CORRECT, 20, 4, 1, null),
                section("CHEM", "Chemistry - Section B (Numerical)", QuestionType.NUMERICAL, 10, 4, 0, 5),
                section("MATH", "Mathematics - Section A (MCQ)", QuestionType.SINGLE_CORRECT, 20, 4, 1, null),
                section("MATH", "Mathematics - Section B (Numerical)", QuestionType.NUMERICAL, 10, 4, 0, 5));
    }

    private static List<SectionTemplate> jeeAdvancedSections() {
        return List.of("PHY:Physics", "CHEM:Chemistry", "MATH:Mathematics").stream()
                .flatMap(s -> {
                    String code = s.split(":")[0];
                    String name = s.split(":")[1];
                    return List.of(
                            section(code, name + " - Section 1 (Single correct)", QuestionType.SINGLE_CORRECT, 4, 3, 1, null),
                            section(code, name + " - Section 2 (One or more correct)", QuestionType.MULTIPLE_CORRECT, 3, 4, 2, null),
                            section(code, name + " - Section 3 (Numerical)", QuestionType.NUMERICAL, 6, 4, 0, null)).stream();
                })
                .toList();
    }
}
