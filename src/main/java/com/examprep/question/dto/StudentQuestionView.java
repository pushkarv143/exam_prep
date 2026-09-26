package com.examprep.question.dto;

import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.NumericFormat;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Media;
import com.examprep.question.model.QuestionContent.Option;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a student may see of a question <b>during a test</b>. There is deliberately no
 * field for the answer key or the solution, so there is nothing to forget to strip.
 *
 * @param parentId       the PARAGRAPH this question belongs to (its passage is sent once, via {@link PassageView})
 * @param language       language of {@code text}/{@code options}; {@code translations} holds the others
 * @param shuffleOptions false when the author forbids shuffling this question's options
 */
public record StudentQuestionView(UUID id, QuestionType type, UUID parentId, String text, List<Media> images,
                                  List<Option> options, List<MatchItem> matchLeft, List<MatchItem> matchRight,
                                  Language language, Map<Language, StudentTranslation> translations,
                                  boolean shuffleOptions, NumericFormat numericFormat) {

    /** A shared reading passage for a group of paragraph-based questions. */
    public record PassageView(UUID id, String text, List<Media> images, Language language,
                              Map<Language, String> translations) {
    }
}
