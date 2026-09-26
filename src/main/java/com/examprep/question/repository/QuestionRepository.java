package com.examprep.question.repository;

import com.examprep.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID>, JpaSpecificationExecutor<Question> {

    /** Number of non-DRAFT tests that use the question (in any version). Native: reads two test columns. */
    @Query(value = """
            SELECT count(DISTINCT tq.test_id) FROM test_questions tq
            JOIN tests t ON t.id = tq.test_id
            WHERE tq.question_id = :questionId AND t.status <> 'DRAFT'
            """, nativeQuery = true)
    long countPublishedTestsUsing(@Param("questionId") UUID questionId);

    long countByParentId(UUID parentId);

    @Query("select q.id from Question q where q.parentId = :parentId and q.publishedVersion is not null "
            + "and q.status <> com.examprep.question.entity.QuestionStatus.ARCHIVED order by q.id")
    List<UUID> findUsableChildIds(@Param("parentId") UUID parentId);

    /** Distinct sub-topics already used in a topic, for the editor's suggestions. */
    @Query("select distinct q.subTopic from Question q where q.topicId = :topicId and q.subTopic is not null "
            + "order by q.subTopic")
    List<String> findSubTopics(@Param("topicId") UUID topicId);
}
