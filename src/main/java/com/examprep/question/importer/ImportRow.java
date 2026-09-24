package com.examprep.question.importer;

import java.util.Map;

/**
 * One data row of an uploaded sheet.
 *
 * @param rowNumber 1-based row number as the user sees it in Excel (header = row 1)
 * @param values    cell values keyed by <b>normalised</b> header (see {@link SpreadsheetParser#normalizeHeader})
 */
public record ImportRow(int rowNumber, Map<String, String> values) {

    /** Trimmed value, or null when the column is absent or blank. */
    public String get(String normalizedHeader) {
        String v = values.get(normalizedHeader);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public String required(String normalizedHeader) {
        String v = get(normalizedHeader);
        if (v == null) {
            throw new IllegalArgumentException("'" + normalizedHeader + "' is required");
        }
        return v;
    }
}
