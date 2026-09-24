package com.examprep.catalog.service;

import com.examprep.catalog.dto.CatalogDtos.ExamDto;
import com.examprep.catalog.dto.CatalogTreeDto;
import com.examprep.catalog.dto.CatalogTreeDto.ChapterNode;
import com.examprep.catalog.dto.CatalogTreeDto.SubjectNode;
import com.examprep.catalog.dto.CatalogTreeDto.TopicNode;
import com.examprep.catalog.dto.ExamListDto;
import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.entity.Chapter;
import com.examprep.catalog.entity.Exam;
import com.examprep.catalog.entity.Subject;
import com.examprep.catalog.entity.Topic;
import com.examprep.catalog.mapper.CatalogMapper;
import com.examprep.catalog.repository.ChapterRepository;
import com.examprep.catalog.repository.ExamRepository;
import com.examprep.catalog.repository.SubjectRepository;
import com.examprep.catalog.repository.TopicRepository;
import com.examprep.common.cache.CacheNames;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.common.entity.BaseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the catalog. The public reads are cached in Redis because they are hit
 * on every page load. Writes go through {@link CatalogAdminService}, which evicts after commit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final TopicRepository topicRepository;
    private final CatalogMapper mapper;

    // ------------------------------------------------------------------ public (cached)

    @Cacheable(cacheNames = CacheNames.CATALOG_EXAMS, key = "'all'")
    public ExamListDto listActiveExams() {
        return new ExamListDto(examRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(mapper::toDto).toList());
    }

    /** Active-only tree. {@code examCode} must already be upper-cased by the caller (it is the cache key). */
    @Cacheable(cacheNames = CacheNames.CATALOG_TREE, key = "#examCode")
    public CatalogTreeDto getActiveTree(String examCode) {
        Exam exam = examRepository.findByCode(examCode)
                .filter(Exam::isActive)
                .orElseThrow(() -> NotFoundException.of("Exam", examCode));
        return buildTree(exam, false);
    }

    // ------------------------------------------------------------------ admin (uncached)

    public List<ExamDto> listAllExams() {
        return examRepository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(mapper::toDto).toList();
    }

    public CatalogTreeDto getTree(UUID examId, boolean includeInactive) {
        Exam exam = examRepository.findById(examId).orElseThrow(() -> NotFoundException.of("Exam", examId));
        return buildTree(exam, includeInactive);
    }

    // ------------------------------------------------------------------ for other modules

    /**
     * Resolves a topic and its ancestors. Throws BAD_REQUEST (not 404), because callers
     * use this to validate an id supplied in a request body.
     */
    public TopicPath resolveTopic(UUID topicId) {
        TopicPath path = resolveTopics(List.of(topicId)).get(topicId);
        if (path == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown topic: " + topicId);
        }
        return path;
    }

    /** Batch resolution with one query per level. Unknown ids are simply absent from the map. */
    public Map<UUID, TopicPath> resolveTopics(Collection<UUID> topicIds) {
        if (topicIds.isEmpty()) {
            return Map.of();
        }
        List<Topic> topics = topicRepository.findAllById(topicIds);
        Map<UUID, Chapter> chapters = byId(chapterRepository.findAllById(
                topics.stream().map(Topic::getChapterId).collect(Collectors.toSet())));
        Map<UUID, Subject> subjects = byId(subjectRepository.findAllById(
                chapters.values().stream().map(Chapter::getSubjectId).collect(Collectors.toSet())));
        Map<UUID, Exam> exams = byId(examRepository.findAllById(
                subjects.values().stream().map(Subject::getExamId).collect(Collectors.toSet())));

        Map<UUID, TopicPath> result = new HashMap<>();
        for (Topic t : topics) {
            Chapter c = chapters.get(t.getChapterId());
            Subject s = subjects.get(c.getSubjectId());
            Exam e = exams.get(s.getExamId());
            result.put(t.getId(), new TopicPath(e.getId(), e.getCode(), s.getId(), s.getName(),
                    c.getId(), c.getName(), t.getId(), t.getName()));
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private CatalogTreeDto buildTree(Exam exam, boolean includeInactive) {
        List<Subject> subjects = subjectRepository.findByExamIdOrderByDisplayOrderAscNameAsc(exam.getId()).stream()
                .filter(s -> includeInactive || s.isActive()).toList();
        List<Chapter> chapters = subjects.isEmpty() ? List.of()
                : chapterRepository.findBySubjectIdInOrderByDisplayOrderAscNameAsc(ids(subjects)).stream()
                .filter(c -> includeInactive || c.isActive()).toList();
        List<Topic> topics = chapters.isEmpty() ? List.of()
                : topicRepository.findByChapterIdInOrderByDisplayOrderAscNameAsc(ids(chapters)).stream()
                .filter(t -> includeInactive || t.isActive()).toList();

        // groupingBy keeps the DB ordering within each group.
        Map<UUID, List<TopicNode>> topicsByChapter = topics.stream().collect(Collectors.groupingBy(
                Topic::getChapterId, Collectors.mapping(mapper::toNode, Collectors.toList())));
        Map<UUID, List<ChapterNode>> chaptersBySubject = chapters.stream().collect(Collectors.groupingBy(
                Chapter::getSubjectId, Collectors.mapping(c -> new ChapterNode(c.getId(), c.getName(),
                        c.getDisplayOrder(), c.isActive(), topicsByChapter.getOrDefault(c.getId(), List.of())),
                        Collectors.toList())));

        List<SubjectNode> subjectNodes = subjects.stream()
                .map(s -> new SubjectNode(s.getId(), s.getCode(), s.getName(), s.getDisplayOrder(), s.isActive(),
                        chaptersBySubject.getOrDefault(s.getId(), List.of())))
                .toList();
        return new CatalogTreeDto(mapper.toDto(exam), subjectNodes);
    }

    private static List<UUID> ids(List<? extends BaseEntity> entities) {
        return entities.stream().map(BaseEntity::getId).toList();
    }

    private static <T extends BaseEntity> Map<UUID, T> byId(List<T> entities) {
        return entities.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
    }
}
