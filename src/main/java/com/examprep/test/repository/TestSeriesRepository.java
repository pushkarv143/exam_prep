package com.examprep.test.repository;

import com.examprep.test.entity.SeriesStatus;
import com.examprep.test.entity.TestSeries;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TestSeriesRepository extends JpaRepository<TestSeries, UUID> {

    Optional<TestSeries> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("""
            select s from TestSeries s
            where (:examId is null or s.examId = :examId)
              and (:status is null or s.status = :status)
              and (:free is null or s.free = :free)
              and (:q is null or lower(s.name) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<TestSeries> search(@Param("examId") UUID examId, @Param("status") SeriesStatus status,
                            @Param("free") Boolean free, @Param("q") String q, Pageable pageable);

    /** Public storefront: published and not batch-restricted. */
    @Query("""
            select s from TestSeries s
            where s.status = com.examprep.test.entity.SeriesStatus.PUBLISHED and s.batchRestricted = false
              and (:examId is null or s.examId = :examId)
              and (:free is null or s.free = :free)
              and (:q is null or lower(s.name) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<TestSeries> searchPublic(@Param("examId") UUID examId, @Param("free") Boolean free,
                                  @Param("q") String q, Pageable pageable);
}
