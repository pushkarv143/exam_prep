package com.examprep.batch.repository;

import com.examprep.batch.dto.BatchDtos.BatchMemberDto;
import com.examprep.batch.entity.BatchMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BatchMemberRepository extends JpaRepository<BatchMember, BatchMember.Id> {

    /** Idempotent add. Returns 1 if inserted, 0 if already a member. */
    @Modifying
    @Query(value = """
            INSERT INTO batch_members (batch_id, user_id, joined_at) VALUES (:batchId, :userId, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int addMember(@Param("batchId") UUID batchId, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from BatchMember m where m.id.batchId = :batchId and m.id.userId = :userId")
    int removeMember(@Param("batchId") UUID batchId, @Param("userId") UUID userId);

    @Query("""
            select count(m) > 0 from BatchMember m
            join Batch b on b.id = m.id.batchId
            where m.id.userId = :userId and m.id.batchId in :batchIds and b.active = true
            """)
    boolean isMemberOfAnyActive(@Param("userId") UUID userId, @Param("batchIds") Collection<UUID> batchIds);

    @Query("select m.id.batchId from BatchMember m where m.id.userId = :userId")
    List<UUID> findBatchIdsByUserId(@Param("userId") UUID userId);

    @Query(value = """
            select new com.examprep.batch.dto.BatchDtos$BatchMemberDto(u.id, u.fullName, u.email, u.phone, m.joinedAt)
            from BatchMember m join User u on u.id = m.id.userId
            where m.id.batchId = :batchId
            """,
            countQuery = "select count(m) from BatchMember m where m.id.batchId = :batchId")
    Page<BatchMemberDto> findMembers(@Param("batchId") UUID batchId, Pageable pageable);

    long countByIdBatchId(UUID batchId);
}
