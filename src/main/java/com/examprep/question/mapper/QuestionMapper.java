package com.examprep.question.mapper;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.question.dto.QuestionDto;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.entity.Question;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written rather than MapStruct: both sources (Question and TopicPath) have
 * {@code topicId}/{@code examId} properties, and explicit code is clearer than a wall
 * of {@code @Mapping} disambiguation.
 */
@Component
public class QuestionMapper {

    private static final int PREVIEW_LENGTH = 180;

    public QuestionDto toDto(Question q, TopicPath topic, boolean usedInPublishedTest) {
        return new QuestionDto(q.getId(), q.getType(), q.getDifficulty(), q.getLanguage(), topic, q.getParentId(),
                q.getContent(), q.getAnswerKey(), q.getDefaultMarks(), q.getDefaultNegativeMarks(), q.getStatus(),
                q.getSource(), q.getYear(), sortedTags(q), usedInPublishedTest, q.getCreatedBy(), q.getCreatedAt(),
                q.getUpdatedAt());
    }

    public QuestionSummaryDto toSummary(Question q, TopicPath topic) {
        return new QuestionSummaryDto(q.getId(), q.getType(), q.getDifficulty(), q.getLanguage(), topic,
                preview(q), q.getDefaultMarks(), q.getDefaultNegativeMarks(), q.getStatus(), sortedTags(q),
                q.getCreatedAt());
    }

    private static List<String> sortedTags(Question q) {
        return q.getTags().stream().sorted().toList();
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
