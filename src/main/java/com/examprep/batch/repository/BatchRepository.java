package com.examprep.batch.repository;

import com.examprep.batch.entity.Batch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    boolean existsByCode(String code);

    @Query("""
            select b from Batch b
            where (:examId is null or b.examId = :examId)
              and (:q is null or lower(b.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(b.code) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Batch> search(@Param("examId") UUID examId, @Param("q") String q, Pageable pageable);
}
