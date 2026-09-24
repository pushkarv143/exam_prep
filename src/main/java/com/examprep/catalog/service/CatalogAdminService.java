package com.examprep.catalog.service;

import com.examprep.catalog.dto.CatalogDtos.ChapterDto;
import com.examprep.catalog.dto.CatalogDtos.ExamDto;
import com.examprep.catalog.dto.CatalogDtos.SubjectDto;
import com.examprep.catalog.dto.CatalogDtos.TopicDto;
import com.examprep.catalog.dto.CatalogRequests.CreateChapterRequest;
import com.examprep.catalog.dto.CatalogRequests.CreateExamRequest;
import com.examprep.catalog.dto.CatalogRequests.CreateSubjectRequest;
import com.examprep.catalog.dto.CatalogRequests.CreateTopicRequest;
import com.examprep.catalog.dto.CatalogRequests.UpdateNodeRequest;
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
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Write side of the catalog. Every mutation schedules a cache eviction that runs after commit.
 *
 * <p>Nodes are never hard-deleted. Questions, tests and analytics reference them, so
 * "delete" means {@code active = false}. Inactive nodes disappear from the public tree
 * but existing data stays intact.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogAdminService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final TopicRepository topicRepository;
    private final CatalogMapper mapper;
    private final CatalogCacheEvictor cacheEvictor;

    // ------------------------------------------------------------------ exams

    public ExamDto createExam(CreateExamRequest req) {
        String code = req.code().toUpperCase();
        if (examRepository.existsByCode(code)) {
            throw duplicate("Exam code " + code);
        }
        Exam exam = new Exam();
        exam.setCode(code);
        exam.setName(req.name().trim());
        exam.setDescription(req.description());
        exam.setDisplayOrder(req.displayOrder());
        examRepository.save(exam);
        cacheEvictor.evictAfterCommit();
        return mapper.toDto(exam);
    }

    public ExamDto updateExam(UUID id, UpdateNodeRequest req) {
        Exam exam = examRepository.findById(id).orElseThrow(() -> NotFoundException.of("Exam", id));
        exam.setName(req.name().trim());
        exam.setDescription(req.description());
        exam.setDisplayOrder(req.displayOrder());
        exam.setActive(req.active());
        cacheEvictor.evictAfterCommit();
        return mapper.toDto(exam);
    }

    // ------------------------------------------------------------------ subjects

    public SubjectDto createSubject(CreateSubjectRequest req) {
        if (!examRepository.existsById(req.examId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown exam: " + req.examId());
        }
        String code = req.code().toUpperCase();
        if (subjectRepository.existsByExamIdAndCode(req.examId(), code)) {
            throw duplicate("Subject code " + code + " in this exam");
        }
        Subject subject = new Subject();
        subject.setExamId(req.examId());
        subject.setCode(code);
        subject.setName(req.name().trim());
        subject.setDisplayOrder(req.displayOrder());
        subjectRepository.save(subject);
        cacheEvictor.evictAfterCommit();
        return mapper.toDto(subject);
    }

    public SubjectDto updateSubject(UUID id, UpdateNodeRequest req) {
        Subject subject = subjectRepository.findById(id).orElseThrow(() -> NotFoundException.of("Subject", id));
        subject.setName(req.name().trim());
        subject.setDisplayOrder(req.displayOrder());
        subject.setActive(req.active());
        cacheEvictor.evictAfterCommit();
        return mapper.toDto(subject);
    }

    // ------------------------------------------------------------------ chapters

    public ChapterDto createChapter(CreateChapterRequest req) {
        if (!subjectRepository.existsById(req.subjectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown subject: " + req.subjectId());
        }
        return mapper.toDto(newChapter(req.subjectId(), req.name(), req.displayOrder()));
    }

    public ChapterDto updateChapter(UUID id, UpdateNodeRequest req) {
        Chapter chapter = chapterRepository.findById(id).orElseThrow(() -> NotFoundException.of("Chapter", id));
        String name = req.name().trim();
        if (chapterRepository.existsBySubjectIdAndNameIgnoreCaseAndIdNot(chapter.getSubjectId(), name, id)) {
            throw duplicate("Chapter '" + name + "' in this subject");
        }
        chapter.setName(name);
        chapter.setDisplayOrder(req.displayOrder());
        chapter.setActive(req.active());
        cacheEvictor.evictAfterCommit();
        return mapper.toDto(chapter);
    }

    // ------------------------------------------------------------------ topics

    public TopicDto createTopic(CreateTopicRequest req) {
        if (!chapterRepository.existsById(req.chapterId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown chapter: " + req.chapterId());
        }
        return mapper.toDto(newTopic(req.chapterId(), req.name(), req.displayOrder()));
    }

    public TopicDto updateTopic(UUID id, UpdateNodeRequest req) {
        Topic topic = topicRepository.findById(id).orElseThrow(() -> NotFoundException.of("Topic", id));
        String name = req.name().trim();
        if (topicRepository.existsByChapterIdAndNameIgnoreCaseAndIdNot(topic.getChapterId(), name, id)) {
            throw duplicate("Topic '" + name + "' in this chapter");
        }
        topic.setName(name);
        topic.setDisplayOrder(req.displayOrder());
        topic.setActive(req.active());
        cacheEvictor.evictAfterCommit();
        return mapper.toDto(topic);
    }

    // ------------------------------------------------------------------ bulk import support

    /**
     * Resolves a topic by business keys, the way spreadsheets reference it:
     * {@code JEE_MAIN / PHY / "Kinematics" / "Projectile Motion"}. Name matching is
     * case-insensitive. With {@code createMissing} the chapter and topic are created on
     * the fly. Exams and subjects are never auto-created, so a typo in a code cannot
     * pollute the top of the tree.
     */
    public TopicPath resolveOrCreateTopic(String examCode, String subjectCode, String chapterName, String topicName,
                                          boolean createMissing) {
        Exam exam = examRepository.findByCode(examCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Unknown exam code: " + examCode));
        Subject subject = subjectRepository.findByExamIdAndCode(exam.getId(), subjectCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "Unknown subject code '" + subjectCode + "' for exam " + exam.getCode()));

        Chapter chapter = chapterRepository.findBySubjectIdAndNameIgnoreCase(subject.getId(), chapterName.trim())
                .orElseGet(() -> {
                    if (!createMissing) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown chapter: " + chapterName);
                    }
                    return newChapter(subject.getId(), chapterName, 0);
                });
        Topic topic = topicRepository.findByChapterIdAndNameIgnoreCase(chapter.getId(), topicName.trim())
                .orElseGet(() -> {
                    if (!createMissing) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown topic: " + topicName);
                    }
                    return newTopic(chapter.getId(), topicName, 0);
                });

        return new TopicPath(exam.getId(), exam.getCode(), subject.getId(), subject.getName(),
                chapter.getId(), chapter.getName(), topic.getId(), topic.getName());
    }

    // ------------------------------------------------------------------ helpers

    private Chapter newChapter(UUID subjectId, String rawName, int displayOrder) {
        String name = rawName.trim();
        if (chapterRepository.existsBySubjectIdAndNameIgnoreCase(subjectId, name)) {
            throw duplicate("Chapter '" + name + "' in this subject");
        }
        Chapter chapter = new Chapter();
        chapter.setSubjectId(subjectId);
        chapter.setName(name);
        chapter.setDisplayOrder(displayOrder);
        chapterRepository.save(chapter);
        cacheEvictor.evictAfterCommit();
        return chapter;
    }

    private Topic newTopic(UUID chapterId, String rawName, int displayOrder) {
        String name = rawName.trim();
        if (topicRepository.existsByChapterIdAndNameIgnoreCase(chapterId, name)) {
            throw duplicate("Topic '" + name + "' in this chapter");
        }
        Topic topic = new Topic();
        topic.setChapterId(chapterId);
        topic.setName(name);
        topic.setDisplayOrder(displayOrder);
        topicRepository.save(topic);
        cacheEvictor.evictAfterCommit();
        return topic;
    }

    private static BusinessException duplicate(String what) {
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE, what + " already exists");
    }
}
