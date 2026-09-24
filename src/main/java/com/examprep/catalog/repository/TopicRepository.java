package com.examprep.catalog.repository;

import com.examprep.catalog.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findByChapterIdInOrderByDisplayOrderAscNameAsc(Collection<UUID> chapterIds);

    Optional<Topic> findByChapterIdAndNameIgnoreCase(UUID chapterId, String name);

    boolean existsByChapterIdAndNameIgnoreCase(UUID chapterId, String name);

    boolean existsByChapterIdAndNameIgnoreCaseAndIdNot(UUID chapterId, String name, UUID id);
}
