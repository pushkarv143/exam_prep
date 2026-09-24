package com.examprep.catalog.repository;

import com.examprep.catalog.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findBySubjectIdInOrderByDisplayOrderAscNameAsc(Collection<UUID> subjectIds);

    Optional<Chapter> findBySubjectIdAndNameIgnoreCase(UUID subjectId, String name);

    boolean existsBySubjectIdAndNameIgnoreCase(UUID subjectId, String name);

    boolean existsBySubjectIdAndNameIgnoreCaseAndIdNot(UUID subjectId, String name, UUID id);
}
