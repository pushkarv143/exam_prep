package com.examprep.catalog.mapper;

import com.examprep.catalog.dto.CatalogDtos.ChapterDto;
import com.examprep.catalog.dto.CatalogDtos.ExamDto;
import com.examprep.catalog.dto.CatalogDtos.SubjectDto;
import com.examprep.catalog.dto.CatalogDtos.TopicDto;
import com.examprep.catalog.dto.CatalogTreeDto.TopicNode;
import com.examprep.catalog.entity.Chapter;
import com.examprep.catalog.entity.Exam;
import com.examprep.catalog.entity.Subject;
import com.examprep.catalog.entity.Topic;
import org.mapstruct.Mapper;

@Mapper
public interface CatalogMapper {

    ExamDto toDto(Exam exam);

    SubjectDto toDto(Subject subject);

    ChapterDto toDto(Chapter chapter);

    TopicDto toDto(Topic topic);

    TopicNode toNode(Topic topic);
}
