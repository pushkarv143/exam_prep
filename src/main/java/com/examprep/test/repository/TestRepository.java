package com.examprep.test.repository;

import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestRepository extends JpaRepository<Test, UUID> {

    List<Test> findBySeriesIdOrderByDisplayOrderAscCreatedAtAsc(UUID seriesId);

    List<Test> findBySeriesIdAndStatusInOrderByDisplayOrderAscCreatedAtAsc(UUID seriesId,
                                                                        Collection<TestStatus> statuses);

    long countBySeriesIdAndStatusIn(UUID seriesId, Collection<TestStatus> statuses);

    @Query("""
            select t from Test t
            where (:seriesId is null or t.seriesId = :seriesId)
              and (:examId is null or t.examId = :examId)
              and (:status is null or t.status = :status)
              and (:q is null or lower(t.title) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Test> search(@Param("seriesId") UUID seriesId, @Param("examId") UUID examId,
                      @Param("status") TestStatus status, @Param("q") String q, Pageable pageable);

    /** Attempts are owned by the attempt module (Phase 4); only the count is needed here. */
    @Query(value = "SELECT count(*) FROM attempts WHERE test_id = :testId", nativeQuery = true)
    long countAttempts(@Param("testId") UUID testId);
}
