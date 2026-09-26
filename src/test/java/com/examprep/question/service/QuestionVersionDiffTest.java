package com.examprep.question.service;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionContent.Option;
import com.examprep.question.model.QuestionSnapshot;
import com.examprep.question.model.QuestionTranslation;
import com.examprep.question.model.QuestionTranslations;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionVersionDiffTest {

    private final QuestionVersionService service =
            new QuestionVersionService(null, JsonMapper.builder().findAndAddModules().build(), null, null);

    private static QuestionSnapshot s(String text, String correct, Difficulty d, QuestionTranslations t) {
        return new QuestionSnapshot(QuestionType.SINGLE_CORRECT, d, Language.EN, null, null, null, null, null, null,
                new QuestionContent(text, null, List.of(new Option("A", "1", null), new Option("B", "2", null)),
                        null, null, null, null),
                new AnswerKey(List.of(correct), null, null, null), t, BigDecimal.valueOf(4), BigDecimal.ONE, null,
                null, null, null, null, null, List.of(), List.of());
    }

    @Test
    void reports_the_changed_areas_in_short_form() {
        QuestionSnapshot v1 = s("Old text", "A", Difficulty.EASY, null);
        QuestionSnapshot v2 = s("New text", "B", Difficulty.HARD, QuestionTranslations.of(
                Map.of(Language.HI, new QuestionTranslation("नया", null, null, null, null, null))));
        assertThat(service.changedFields(v1, v2))
                .containsExactlyInAnyOrder("content.text", "answerKey", "difficulty", "translations.HI");
        assertThat(service.changedFields(v1, v1)).isEmpty();
        assertThat(service.changedFields(null, v1)).isEmpty();
    }
}
