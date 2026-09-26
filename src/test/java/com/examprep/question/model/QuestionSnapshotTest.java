package com.examprep.question.model;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.QuestionContent.Option;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionSnapshotTest {

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private static QuestionSnapshot snapshot(String text, AnswerKey key, List<Option> options,
                                             QuestionTranslations translations, String marks) {
        return new QuestionSnapshot(QuestionType.SINGLE_CORRECT, Difficulty.EASY, Language.EN, null, UUID.randomUUID(),
                null, null, null, null, new QuestionContent(text, null, options, null, null, null, null), key,
                translations, new BigDecimal(marks), BigDecimal.ONE, null, null, null, null, null, null,
                List.of("kinematics"), List.of());
    }

    private static final List<Option> ABCD = List.of(new Option("A", "1", null), new Option("B", "2", null),
            new Option("C", "3", null), new Option("D", "4", null));

    @Test
    void wording_changes_score_alike_but_key_or_option_changes_do_not() {
        AnswerKey b = new AnswerKey(List.of("B"), null, null, null);
        QuestionSnapshot v1 = snapshot("A car starts from rest", b, ABCD, null, "4");
        QuestionSnapshot typo = snapshot("A car starts from rest (typo fixed)", b, ABCD, QuestionTranslations.of(
                Map.of(Language.HI, new QuestionTranslation("एक कार", null, null, null, null, null))), "4");
        QuestionSnapshot key = snapshot("A car starts from rest", new AnswerKey(List.of("C"), null, null, null), ABCD,
                null, "4");
        QuestionSnapshot fewerOptions = snapshot("A car starts from rest", b, ABCD.subList(0, 3), null, "4");

        assertThat(typo.scoresLike(v1)).isTrue();
        assertThat(key.scoresLike(v1)).isFalse();
        assertThat(fewerOptions.scoresLike(v1)).isFalse();
    }

    @Test
    void marks_compare_by_value_and_json_round_trip_keeps_translations() throws Exception {
        QuestionSnapshot a = snapshot("Q", new AnswerKey(List.of("A"), null, null, null), ABCD,
                QuestionTranslations.of(Map.of(Language.HI,
                        new QuestionTranslation("प्रश्न", null, Map.of("A", "एक"), null, null, "हल"))), "4.00");
        QuestionSnapshot b = snapshot("Q", new AnswerKey(List.of("A"), null, null, null), ABCD, a.translations(), "4");
        assertThat(new QuestionSnapshot(a.type(), a.difficulty(), a.language(), a.examId(), b.subjectId(), a.chapterId(),
                a.topicId(), a.subTopic(), a.parentId(), a.content(), a.answerKey(), a.translations(), a.marks(),
                a.negativeMarks(), a.sourceType(), a.source(), a.year(), a.pyqShift(), a.expectedTimeSec(),
                a.cognitiveLevel(), a.tags(), a.concepts())).isEqualTo(b);

        String json = JSON.writeValueAsString(a);
        assertThat(json).contains("\"translations\":{\"HI\":");
        QuestionSnapshot back = JSON.readValue(json, QuestionSnapshot.class);
        assertThat(back).isEqualTo(a);
        assertThat(back.translations().get(Language.HI).orElseThrow().options()).containsEntry("A", "एक");
    }

    @Test
    void translation_check_lists_missing_texts() {
        QuestionContent c = new QuestionContent("Q", null, ABCD, null, null, null,
                new QuestionContent.Solution("Because", null, null));
        assertThat(TranslationCheck.missing(QuestionType.SINGLE_CORRECT, c,
                new QuestionTranslation("प्रश्न", null, Map.of("A", "1", "B", "2"), null, null, null)))
                .containsExactly("options.C", "options.D", "solution");
        assertThat(TranslationCheck.missing(QuestionType.SINGLE_CORRECT, c, null)).hasSize(6);
    }

    @Test
    void empty_translations_are_dropped() {
        assertThat(QuestionTranslations.of(Map.of(Language.HI,
                new QuestionTranslation(" ", null, Map.of("A", ""), null, null, null))).isEmpty()).isTrue();
    }
}
