package com.examprep.question.mapper;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.question.dto.QuestionDto;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.Question;
import com.examprep.question.model.QuestionTranslation;
import com.examprep.question.model.TranslationCheck;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hand-written rather than MapStruct: both sources (Question and TopicPath) have
 * {@code topicId}/{@code examId} properties, and explicit code is clearer than a wall
 * of {@code @Mapping} disambiguation.
 */
@Component
public class QuestionMapper {

    private static final int PREVIEW_LENGTH = 180;

    /**
     * Extra facts for the full DTO that the entity does not hold.
     *
     * @param names user names by id (creator, reviewer, submitter)
     */
    public record Extras(Map<UUID, String> names, int openComments, long usedInPublishedTests, List<String> actions,
                         Instant now) {
    }

    public QuestionDto toDto(Question q, TopicPath topic, Extras x) {
        Map<Language, QuestionTranslation> translations = q.getTranslations().without(q.getLanguage()).asMap();
        Map<Language, List<String>> missing = new EnumMap<>(Language.class);
        translations.forEach((lang, t) -> missing.put(lang, TranslationCheck.missing(q.getType(), q.getContent(), t)));
        QuestionDto.ReviewState review = q.getReviewRequestedAt() == null && q.getReviewerId() == null ? null
                : new QuestionDto.ReviewState(q.getReviewerId(), name(x.names(), q.getReviewerId()), q.getSubmittedBy(),
                name(x.names(), q.getSubmittedBy()), q.getReviewRequestedAt(), q.getReviewDueAt(),
                q.getReviewDueAt() != null && q.getReviewDueAt().isBefore(x.now()));
        return new QuestionDto(q.getId(), q.getType(), q.getDifficulty(), q.getLanguage(), topic, q.getSubTopic(),
                q.getParentId(), q.getContent(), q.getAnswerKey(), translations, missing, q.getDefaultMarks(),
                q.getDefaultNegativeMarks(), q.getStatus(), q.getSourceType(), q.getSource(), q.getYear(),
                q.getPyqShift(), q.getExpectedTimeSec() == null ? null : q.getExpectedTimeSec().intValue(),
                q.getCognitiveLevel(), sorted(q.getTags()), sorted(q.getConcepts()), q.getCurrentVersion(),
                q.getPublishedVersion(), q.getPublishedAt(), review, x.openComments(), x.usedInPublishedTests(),
                q.getCreatedBy(), name(x.names(), q.getCreatedBy()), q.getCreatedAt(), q.getUpdatedAt(), x.actions());
    }

    public QuestionSummaryDto toSummary(Question q, TopicPath topic, Map<UUID, String> names) {
        List<Language> languages = new ArrayList<>();
        languages.add(q.getLanguage());
        q.getTranslations().without(q.getLanguage()).asMap().keySet().forEach(languages::add);
        return new QuestionSummaryDto(q.getId(), q.getType(), q.getDifficulty(), q.getLanguage(), languages, topic,
                q.getSubTopic(), preview(q), q.getDefaultMarks(), q.getDefaultNegativeMarks(), q.getStatus(),
                q.getCurrentVersion(), q.getPublishedVersion(), q.getReviewerId(), name(names, q.getReviewerId()),
                q.getReviewDueAt(), q.getSourceType(), q.getYear(), sorted(q.getTags()), q.getCreatedBy(),
                q.getCreatedAt(), q.getUpdatedAt());
    }

    /** Null-safe: immutable maps reject {@code get(null)}. */
    private static String name(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    private static List<String> sorted(java.util.Set<String> values) {
        return values.stream().sorted().toList();
    }

    /** First ~180 characters of the stem (or passage), whitespace-collapsed, LaTeX kept as-is. */
    private static String preview(Question q) {
        String text = q.getContent().text() != null ? q.getContent().text() : q.getContent().paragraph();
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= PREVIEW_LENGTH ? collapsed : collapsed.substring(0, PREVIEW_LENGTH) + "…";
    }
}
