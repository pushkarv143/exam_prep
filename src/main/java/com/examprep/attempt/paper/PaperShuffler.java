package com.examprep.attempt.paper;

import com.examprep.attempt.paper.PaperDto.PaperQuestion;
import com.examprep.attempt.paper.PaperDto.PaperSection;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.QuestionContent.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Per-student paper variant, <b>deterministic</b> for a given attempt seed, so a resumed
 * attempt shows the exact same order. This makes copying between neighbours harder.
 *
 * <ul>
 *   <li>Section order never changes (NTA keeps subjects fixed).</li>
 *   <li>Within a section, questions of the same paragraph stay together and in order,
 *       and the groups are shuffled.</li>
 *   <li>Options of single/multiple-correct questions are shuffled for display only.
 *       Their ids (A-D) are kept, so answers are stored and evaluated against the
 *       canonical labels. The UI shows positional numbers (1)-(4), like NTA. A question can
 *       forbid shuffling, and single options can be pinned (e.g. "None of these" stays last).</li>
 * </ul>
 */
public final class PaperShuffler {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private PaperShuffler() {
    }

    public static PaperDto forAttempt(PaperDto paper, long seed, boolean shuffleQuestions, boolean shuffleOptions) {
        List<PaperSection> sections = new ArrayList<>(paper.sections().size());
        int number = 0;
        for (int si = 0; si < paper.sections().size(); si++) {
            PaperSection section = paper.sections().get(si);
            List<PaperQuestion> ordered = shuffleQuestions
                    ? shuffleGroups(section.questions(), new Random(seed ^ (GOLDEN * (si + 1))))
                    : section.questions();

            List<PaperQuestion> renumbered = new ArrayList<>(ordered.size());
            for (PaperQuestion q : ordered) {
                List<Option> options = q.options();
                if (shuffleOptions && q.shuffleOptions() && (q.type() == QuestionType.SINGLE_CORRECT
                        || q.type() == QuestionType.MULTIPLE_CORRECT) && options.size() > 1) {
                    options = shuffleUnpinned(options, new Random(seed ^ q.questionId().getMostSignificantBits()
                            ^ q.questionId().getLeastSignificantBits()));
                }
                renumbered.add(q.withNumberAndOptions(++number, options));
            }
            sections.add(new PaperSection(section.id(), section.name(), section.subjectId(), section.instructions(),
                    section.maxQuestionsToAttempt(), List.copyOf(renumbered)));
        }
        return new PaperDto(paper.testId(), paper.title(), paper.durationMinutes(), paper.totalMarks(),
                paper.totalQuestions(), List.copyOf(sections), paper.passages());
    }

    /** Shuffles the options that are not pinned; pinned options keep their position. */
    static List<Option> shuffleUnpinned(List<Option> options, Random random) {
        List<Option> free = new ArrayList<>();
        for (Option o : options) {
            if (!o.keepsPosition()) {
                free.add(o);
            }
        }
        Collections.shuffle(free, random);
        List<Option> out = new ArrayList<>(options.size());
        int next = 0;
        for (Option o : options) {
            out.add(o.keepsPosition() ? o : free.get(next++));
        }
        return List.copyOf(out);
    }

    /** Groups consecutive questions sharing a paragraph, shuffles the groups, then flattens. */
    private static List<PaperQuestion> shuffleGroups(List<PaperQuestion> questions, Random random) {
        List<List<PaperQuestion>> groups = new ArrayList<>();
        for (PaperQuestion q : questions) {
            List<PaperQuestion> last = groups.isEmpty() ? null : groups.getLast();
            if (last != null && q.paragraphId() != null
                    && Objects.equals(last.getFirst().paragraphId(), q.paragraphId())) {
                last.add(q);
            } else {
                List<PaperQuestion> group = new ArrayList<>();
                group.add(q);
                groups.add(group);
            }
        }
        Collections.shuffle(groups, random);
        return groups.stream().flatMap(List::stream).toList();
    }
}
