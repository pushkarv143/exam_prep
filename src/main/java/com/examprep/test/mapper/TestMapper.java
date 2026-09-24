package com.examprep.test.mapper;

import com.examprep.test.dto.SeriesDtos.SeriesDto;
import com.examprep.test.dto.TestDtos.TestDto;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestSeries;
import org.mapstruct.Mapper;

@Mapper
public interface TestMapper {

    TestDto toDto(Test test);

    SeriesDto toDto(TestSeries series, long testCount, long enrollmentCount);
}
