package com.examprep.audit;

import com.examprep.audit.service.JsonDiff;
import com.examprep.audit.service.SensitiveData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDiffTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void reports_changed_added_and_removed_fields_with_paths() throws Exception {
        JsonNode before = json.readTree("{\"title\":\"Mock 1\",\"duration\":180,\"meta\":{\"free\":false,\"old\":1}}");
        JsonNode after = json.readTree("{\"title\":\"Mock 1 (revised)\",\"duration\":180,\"meta\":{\"free\":true,\"new\":2}}");

        JsonNode diff = JsonDiff.diff(before, after);

        assertThat(diff.toString())
                .contains("{\"path\":\"title\",\"op\":\"changed\",\"from\":\"Mock 1\",\"to\":\"Mock 1 (revised)\"}")
                .contains("{\"path\":\"meta.free\",\"op\":\"changed\",\"from\":false,\"to\":true}")
                .contains("{\"path\":\"meta.old\",\"op\":\"removed\",\"from\":1}")
                .contains("{\"path\":\"meta.new\",\"op\":\"added\",\"to\":2}")
                .doesNotContain("duration");
    }

    @Test
    void scalar_arrays_report_added_and_removed_items() throws Exception {
        JsonNode diff = JsonDiff.diff(json.readTree("{\"permissions\":[\"a\",\"b\"]}"),
                json.readTree("{\"permissions\":[\"b\",\"c\"]}"));

        assertThat(diff).hasSize(2);
        assertThat(diff.toString()).contains("\"op\":\"added\",\"to\":[\"c\"]").contains("\"op\":\"removed\",\"from\":[\"a\"]");
    }

    @Test
    void create_and_delete_are_whole_document_changes() throws Exception {
        JsonNode doc = json.readTree("{\"name\":\"X\"}");
        assertThat(JsonDiff.diff(null, doc).toString()).contains("\"op\":\"added\"");
        assertThat(JsonDiff.diff(doc, null).toString()).contains("\"op\":\"removed\"");
        assertThat(JsonDiff.diff(doc, doc)).isEmpty();
    }

    @Test
    void secrets_are_masked_recursively() throws Exception {
        JsonNode node = json.readTree(
                "{\"password\":\"hunter2\",\"user\":{\"refreshToken\":\"abc\",\"name\":\"A\"},\"list\":[{\"otp\":\"123456\"}]}");
        SensitiveData.mask(node);
        assertThat(node.toString()).doesNotContain("hunter2").doesNotContain("abc").doesNotContain("123456")
                .contains("\"name\":\"A\"");
    }
}
