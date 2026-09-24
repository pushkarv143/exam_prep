package com.examprep.batch.service;

import com.examprep.batch.dto.BatchDtos.AddMembersRequest;
import com.examprep.batch.dto.BatchDtos.AddMembersResult;
import com.examprep.batch.dto.BatchDtos.BatchDto;
import com.examprep.batch.dto.BatchDtos.BatchMemberDto;
import com.examprep.batch.dto.BatchDtos.CreateBatchRequest;
import com.examprep.batch.dto.BatchDtos.UpdateBatchRequest;
import com.examprep.batch.entity.Batch;
import com.examprep.batch.repository.BatchMemberRepository;
import com.examprep.batch.repository.BatchRepository;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batchRepository;
    private final BatchMemberRepository memberRepository;
    private final UserService userService;

    // ------------------------------------------------------------------ admin

    @Transactional
    public BatchDto create(CreateBatchRequest req) {
        String code = req.code().toUpperCase();
        if (batchRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Batch code " + code + " already exists");
        }
        validateDates(req.startDate(), req.endDate());
        Batch batch = new Batch();
        batch.setCode(code);
        batch.setName(req.name().trim());
        batch.setDescription(req.description());
        batch.setExamId(req.examId());
        batch.setStartDate(req.startDate());
        batch.setEndDate(req.endDate());
        batchRepository.save(batch);
        return toDto(batch, 0);
    }

    @Transactional
    public BatchDto update(UUID id, UpdateBatchRequest req) {
        Batch batch = load(id);
        validateDates(req.startDate(), req.endDate());
        batch.setName(req.name().trim());
        batch.setDescription(req.description());
        batch.setExamId(req.examId());
        batch.setStartDate(req.startDate());
        batch.setEndDate(req.endDate());
        batch.setActive(req.active());
        return toDto(batch, memberRepository.countByIdBatchId(id));
    }

    @Transactional(readOnly = true)
    public BatchDto get(UUID id) {
        return toDto(load(id), memberRepository.countByIdBatchId(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchDto> search(UUID examId, String q, Pageable pageable) {
        return PageResponse.of(batchRepository.search(examId, blankToNull(q), pageable),
                b -> toDto(b, memberRepository.countByIdBatchId(b.getId())));
    }

    @Transactional
    public AddMembersResult addMembers(UUID batchId, AddMembersRequest req) {
        load(batchId);
        Set<UUID> ids = new LinkedHashSet<>(req.userIds() == null ? List.of() : req.userIds());
        List<UUID> unknownIds = new ArrayList<>(ids);
        unknownIds.removeAll(userService.findExistingIds(ids));
        ids.removeAll(unknownIds);

        List<String> unknownEmails = new ArrayList<>();
        if (req.emails() != null && !req.emails().isEmpty()) {
            Map<String, UUID> byEmail = userService.findIdsByEmails(req.emails());
            for (String email : req.emails()) {
                UUID id = byEmail.get(email.trim().toLowerCase());
                if (id == null) {
                    unknownEmails.add(email);
                } else {
                    ids.add(id);
                }
            }
        }

        int added = 0;
        for (UUID userId : ids) {
            added += memberRepository.addMember(batchId, userId);
        }
        return new AddMembersResult(added, ids.size() - added, unknownEmails, unknownIds);
    }

    @Transactional
    public void removeMember(UUID batchId, UUID userId) {
        if (memberRepository.removeMember(batchId, userId) == 0) {
            throw new NotFoundException("User " + userId + " is not a member of batch " + batchId);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchMemberDto> members(UUID batchId, Pageable pageable) {
        load(batchId);
        return PageResponse.of(memberRepository.findMembers(batchId, pageable));
    }

    // ------------------------------------------------------------------ for other modules

    @Transactional(readOnly = true)
    public boolean isMemberOfAnyActive(UUID userId, Collection<UUID> batchIds) {
        return !batchIds.isEmpty() && memberRepository.isMemberOfAnyActive(userId, batchIds);
    }

    // ------------------------------------------------------------------ helpers

    private Batch load(UUID id) {
        return batchRepository.findById(id).orElseThrow(() -> NotFoundException.of("Batch", id));
    }

    private static void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endDate must not be before startDate");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BatchDto toDto(Batch b, long memberCount) {
        return new BatchDto(b.getId(), b.getCode(), b.getName(), b.getDescription(), b.getExamId(), b.getStartDate(),
                b.getEndDate(), b.isActive(), memberCount, b.getCreatedAt());
    }
}
