package com.examprep.question.repository;

import com.examprep.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID>, JpaSpecificationExecutor<Question> {

    /**
     * True if the question is part of any non-DRAFT test. Students may already hold (or
     * be evaluated against) its answer key, so the key must not change.
     * This is a native query because the test module's entities arrive in Phase 3. It
     * reads only two columns of the test tables.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM test_questions tq
                JOIN tests t ON t.id = tq.test_id
                WHERE tq.question_id = :questionId AND t.status <> 'DRAFT')
            """, nativeQuery = true)
    boolean isUsedInPublishedTest(@Param("questionId") UUID questionId);

    long countByParentId(UUID parentId);

    @Query("select q.id from Question q where q.parentId = :parentId "
            + "and q.status = com.examprep.question.entity.QuestionStatus.ACTIVE order by q.id")
    java.util.List<UUID> findActiveChildIds(@Param("parentId") UUID parentId);
}
