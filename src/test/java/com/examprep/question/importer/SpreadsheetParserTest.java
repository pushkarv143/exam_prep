package com.examprep.question.importer;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpreadsheetParserTest {

    @Test
    void csv_with_bom_quotes_and_blank_lines() {
        String csv = "﻿Exam Code,question_text,Option A\n"
                + "JEE_MAIN,\"Commas, inside $\\frac{1}{2}$\",x\n"
                + "\n"
                + ",,\n"
                + "NEET,Second,y\n";
        SpreadsheetParser.ParsedSheet sheet = SpreadsheetParser.parse("q.csv",
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(sheet.headers()).containsExactly("examcode", "questiontext", "optiona");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0).get("examcode")).isEqualTo("JEE_MAIN");   // BOM stripped
        assertThat(sheet.rows().get(0).get("questiontext")).isEqualTo("Commas, inside $\\frac{1}{2}$");
        assertThat(sheet.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(sheet.rows().get(1).get("examcode")).isEqualTo("NEET");
    }

    @Test
    void xlsx_numeric_cells_are_read_as_displayed() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet s = wb.createSheet("Questions");
            XSSFRow header = s.createRow(0);
            header.createCell(0).setCellValue("Correct Answer");
            header.createCell(1).setCellValue("Year");
            XSSFRow r1 = s.createRow(1);
            r1.createCell(0).setCellValue(2.5);
            r1.createCell(1).setCellValue(2024);
            s.createRow(2);                          // empty row is skipped
            XSSFRow r3 = s.createRow(3);
            r3.createCell(0).setCellValue("B");
            wb.write(out);
            bytes = out.toByteArray();
        }

        SpreadsheetParser.ParsedSheet sheet = SpreadsheetParser.parse("Q.XLSX", new ByteArrayInputStream(bytes));

        assertThat(sheet.headers()).containsExactly("correctanswer", "year");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0).get("correctanswer")).isEqualTo("2.5");
        assertThat(sheet.rows().get(0).get("year")).isEqualTo("2024");
        assertThat(sheet.rows().get(1).rowNumber()).isEqualTo(4);
    }

    @Test
    void unsupported_extension_is_rejected() {
        assertThatThrownBy(() -> SpreadsheetParser.parse("q.xls", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void corrupt_xlsx_is_a_bad_request_not_a_500() {
        assertThatThrownBy(() -> SpreadsheetParser.parse("q.xlsx",
                new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
