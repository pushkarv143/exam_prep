package com.examprep.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination DTO. It exists so that Spring's {@code PageImpl} JSON, which is
 * not a stable contract, never leaks into the public API.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                              boolean last) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return of(page.map(mapper));
    }
}
