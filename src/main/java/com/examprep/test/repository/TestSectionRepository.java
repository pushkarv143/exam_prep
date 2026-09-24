package com.examprep.test.repository;

import com.examprep.test.entity.TestSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestSectionRepository extends JpaRepository<TestSection, UUID> {

    List<TestSection> findByTestIdOrderByDisplayOrderAscCreatedAtAsc(UUID testId);

    Optional<TestSection> findByIdAndTestId(UUID id, UUID testId);
}
