package com.examprep.attempt.paper;

import com.examprep.attempt.paper.PaperDto.PaperQuestion;
import com.examprep.attempt.paper.PaperDto.PaperSection;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.QuestionContent.Option;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PaperShufflerTest {

    static final List<Option> ABCD = List.of(new Option("A", "1", null), new Option("B", "2", null),
            new Option("C", "3", null), new Option("D", "4", null));

    private static PaperQuestion q(int n, UUID paragraph, QuestionType type) {
        return new PaperQuestion(new UUID(0, n), n, type, BigDecimal.valueOf(4), BigDecimal.ONE, false, paragraph,
                "Q" + n, List.of(), type == QuestionType.NUMERICAL ? List.of() : ABCD, List.of(), List.of());
    }

    /** Section 1: 20 questions, of which #5-#7 share one paragraph. Section 2: 5 questions. */
    private static PaperDto paper() {
        UUID passage = UUID.randomUUID();
        List<PaperQuestion> s1 = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            s1.add(q(i, i >= 5 && i <= 7 ? passage : null, i % 4 == 0 ? QuestionType.NUMERICAL : QuestionType.SINGLE_CORRECT));
        }
        List<PaperQuestion> s2 = IntStream.rangeClosed(21, 25).mapToObj(i -> q(i, null, QuestionType.SINGLE_CORRECT)).toList();
        return new PaperDto(UUID.randomUUID(), "T", 60, BigDecimal.TEN, 25, List.of(
                new PaperSection(UUID.randomUUID(), "S1", null, null, null, s1),
                new PaperSection(UUID.randomUUID(), "S2", null, null, null, s2)), Map.of());
    }

    private static List<Integer> order(PaperDto p) {
        return p.sections().stream().flatMap(s -> s.questions().stream())
                .map(x -> (int) x.questionId().getLeastSignificantBits()).toList();
    }

    @Test
    void same_seed_gives_identical_paper_so_resume_is_stable() {
        PaperDto base = paper();
        PaperDto a = PaperShuffler.forAttempt(base, 42L, true, true);
        PaperDto b = PaperShuffler.forAttempt(base, 42L, true, true);
        assertThat(a).isEqualTo(b);
        assertThat(order(PaperShuffler.forAttempt(base, 43L, true, true))).isNotEqualTo(order(a));
    }

    @Test
    void shuffles_within_sections_keeps_paragraphs_together_and_renumbers() {
        PaperDto s = PaperShuffler.forAttempt(paper(), 7L, true, false);
        List<Integer> sec1 = s.sections().get(0).questions().stream()
                .map(x -> (int) x.questionId().getLeastSignificantBits()).toList();
        List<Integer> sec2 = s.sections().get(1).questions().stream()
                .map(x -> (int) x.questionId().getLeastSignificantBits()).toList();

        assertThat(sec1).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(sec2).containsExactlyInAnyOrder(21, 22, 23, 24, 25);   // never crosses sections
        int p = sec1.indexOf(5);
        assertThat(sec1.subList(p, p + 3)).containsExactly(5, 6, 7);          // passage group intact and in order
        assertThat(s.sections().stream().flatMap(x -> x.questions().stream()).map(PaperQuestion::number))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 25).boxed().toList());
    }

    @Test
    void option_shuffle_keeps_canonical_ids_and_skips_numerical() {
        PaperDto s = PaperShuffler.forAttempt(paper(), 99L, false, true);
        List<PaperQuestion> all = s.sections().stream().flatMap(x -> x.questions().stream()).toList();
        assertThat(order(s)).containsExactlyElementsOf(IntStream.rangeClosed(1, 25).boxed().toList());
        all.stream().filter(x -> x.type() == QuestionType.SINGLE_CORRECT)
                .forEach(x -> assertThat(x.options()).containsExactlyInAnyOrderElementsOf(ABCD));
        assertThat(all.stream().filter(x -> x.type() == QuestionType.SINGLE_CORRECT)
                .anyMatch(x -> !x.options().equals(ABCD))).isTrue();
        all.stream().filter(x -> x.type() == QuestionType.NUMERICAL).forEach(x -> assertThat(x.options()).isEmpty());
    }

    @Test
    void no_shuffle_is_identity() {
        PaperDto base = paper();
        assertThat(PaperShuffler.forAttempt(base, 1L, false, false)).isEqualTo(base);
    }
}
