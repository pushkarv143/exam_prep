package com.examprep.question.dto;

import java.util.List;

/**
 * Outcome of a bulk import. Imports are <b>all-or-nothing</b>: if any row is invalid,
 * nothing is saved and every error is reported, so the teacher fixes the sheet and
 * re-uploads it without creating duplicates.
 *
 * @param validRows    rows that passed every check
 * @param importedRows rows actually saved (0 for a dry run or when there are errors)
 * @param errors       per-row problems (capped at {@code MAX_REPORTED_ERRORS})
 */
public record ImportReport(int totalRows, int validRows, int importedRows, boolean dryRun,
                           List<RowError> errors) {

    public record RowError(int row, String message) {
    }
}
