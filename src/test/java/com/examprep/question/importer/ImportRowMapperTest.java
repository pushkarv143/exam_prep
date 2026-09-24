package com.examprep.question.importer;

import com.examprep.question.dto.QuestionRequest;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.service.QuestionValidator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportRowMapperTest {

    private static final UUID TOPIC = UUID.randomUUID();

    private static ImportRow row(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return new ImportRow(2, m);
    }

    @Test
    void maps_multiple_correct_with_aliases_and_tags() {
        QuestionRequest r = ImportRowMapper.toRequest(row(
                "type", "mcq", "difficulty", "hard", "questiontext", "Pick",
                "optiona", "x", "optionb", "y", "optionc", "z",
                "correctanswer", "c; a", "tags", "Tag1, tag2;", "year", "2023"), TOPIC);

        assertThat(r.type()).isEqualTo(QuestionType.MULTIPLE_CORRECT);
        assertThat(r.difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(r.content().options()).extracting("id").containsExactly("A", "B", "C");
        assertThat(r.answerKey().options()).containsExactly("A", "C");
        assertThat(r.tags()).containsExactlyInAnyOrder("Tag1", "tag2");
        assertThat(r.year()).isEqualTo(2023);
        assertThat(r.topicId()).isEqualTo(TOPIC);
    }

    @Test
    void maps_numerical_value_and_tolerance() {
        QuestionRequest r = ImportRowMapper.toRequest(row(
                "type", "INTEGER", "questiontext", "x?", "correctanswer", "0.50", "tolerance", "0.01"), TOPIC);
        assertThat(r.type()).isEqualTo(QuestionType.NUMERICAL);
        assertThat(r.answerKey().value()).isEqualByComparingTo("0.5");
        assertThat(r.answerKey().tolerance()).isEqualByComparingTo("0.01");
    }

    @Test
    void reports_readable_errors() {
        assertThatThrownBy(() -> ImportRowMapper.toRequest(row(
                "type", "essay", "questiontext", "x", "correctanswer", "A"), TOPIC))
                .hasMessageContaining("Unknown type 'essay'");

        assertThatThrownBy(() -> ImportRowMapper.toRequest(row(
                "type", "NUMERICAL", "questiontext", "x", "correctanswer", "two"), TOPIC))
                .hasMessageContaining("must be a number");

        assertThatThrownBy(() -> ImportRowMapper.toRequest(row(
                "type", "SCQ", "questiontext", "x", "correctanswer", "A", "difficulty", "insane"), TOPIC))
                .hasMessageContaining("Invalid difficulty");

        assertThatThrownBy(() -> ImportRowMapper.toRequest(row("type", "SCQ", "correctanswer", "A"), TOPIC))
                .hasMessageContaining("'questiontext' is required");

        assertThatThrownBy(() -> ImportRowMapper.toRequest(row(
                "type", "MATCH", "questiontext", "x", "correctanswer", "P-1"), TOPIC))
                .hasMessageContaining("cannot be bulk-imported");
    }

    /** Guards the downloadable template: every example row must import cleanly. */
    @Test
    void downloadable_template_rows_are_all_valid() {
        SpreadsheetParser.ParsedSheet sheet = SpreadsheetParser.parse("template.csv",
                new ByteArrayInputStream(ImportColumns.TEMPLATE_CSV.getBytes(StandardCharsets.UTF_8)));
        assertThat(sheet.headers()).containsAll(ImportColumns.REQUIRED);

        List<ImportRow> rows = sheet.rows();
        assertThat(rows).hasSize(3);
        QuestionValidator validator = new QuestionValidator();
        for (ImportRow r : rows) {
            QuestionRequest req = ImportRowMapper.toRequest(r, TOPIC);
            assertThatCode(() -> validator.validate(req.type(), req.content(), req.answerKey()))
                    .as("template row %d", r.rowNumber())
                    .doesNotThrowAnyException();
        }
        assertThat(rows.get(0).get("optiona")).isEqualTo("$9\\,\\text{m}$");
    }
}
