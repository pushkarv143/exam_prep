package com.examprep.catalog.dto;

import com.examprep.catalog.dto.CatalogDtos.ExamDto;

import java.util.List;

/** Wrapper so the cached value has one concrete type (a bare {@code List<ExamDto>} loses its element type in JSON). */
public record ExamListDto(List<ExamDto> exams) {
}
