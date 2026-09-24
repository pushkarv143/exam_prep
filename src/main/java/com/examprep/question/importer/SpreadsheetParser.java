package com.examprep.question.importer;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the first sheet of an .xlsx or a UTF-8 .csv (with or without BOM) into rows keyed by header.
 *
 * <p>Headers are normalised: lower-cased, with everything except letters and digits
 * removed. "Question Text", "questionText" and "question_text" all map to
 * {@code questiontext}, so teachers' spreadsheets do not have to match exactly.
 *
 * <p>Cells are read with {@link DataFormatter}, i.e. as displayed. A numeric cell
 * shown as "2.5" arrives as "2.5", not "2.4999999". POI's zip-bomb protection
 * (inflate ratio / entry size limits) stays enabled by default.
 */
public final class SpreadsheetParser {

    public record ParsedSheet(Set<String> headers, List<ImportRow> rows) {
    }

    private SpreadsheetParser() {
    }

    public static ParsedSheet parse(String filename, InputStream in) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx")) {
                return parseExcel(in);
            }
            if (name.endsWith(".csv")) {
                return parseCsv(in);
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Could not read file: " + e.getMessage());
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Upload a .xlsx or .csv file");
    }

    public static String normalizeHeader(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static ParsedSheet parseCsv(InputStream in) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (Reader reader = new InputStreamReader(BOMInputStream.builder().setInputStream(in).get(),
                StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.builder().setReader(reader).setFormat(format).get()) {

            List<String> rawHeaders = parser.getHeaderNames();
            Set<String> headers = new LinkedHashSet<>();
            rawHeaders.forEach(h -> headers.add(normalizeHeader(h)));

            List<ImportRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> values = new HashMap<>();
                for (int i = 0; i < rawHeaders.size() && i < record.size(); i++) {
                    values.put(normalizeHeader(rawHeaders.get(i)), record.get(i));
                }
                if (values.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                    // +1 for the header line: row numbers match what the user sees in an editor.
                    rows.add(new ImportRow((int) record.getRecordNumber() + 1, values));
                }
            }
            return new ParsedSheet(headers, rows);
        }
    }

    static ParsedSheet parseExcel(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Workbook has no sheets");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "First row must contain column headers");
            }
            Map<Integer, String> headerByColumn = new HashMap<>();
            Set<String> headers = new LinkedHashSet<>();
            for (Cell cell : headerRow) {
                String h = normalizeHeader(formatter.formatCellValue(cell));
                if (!h.isEmpty()) {
                    headerByColumn.put(cell.getColumnIndex(), h);
                    headers.add(h);
                }
            }

            List<ImportRow> rows = new ArrayList<>();
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                headerByColumn.forEach((col, header) -> {
                    Cell cell = row.getCell(col);
                    if (cell != null) {
                        values.put(header, formatter.formatCellValue(cell, evaluator));
                    }
                });
                if (values.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                    rows.add(new ImportRow(r + 1, values));
                }
            }
            return new ParsedSheet(headers, rows);
        }
    }
}
