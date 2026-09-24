package com.examprep.question.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerKeyTest {

    @Test
    void options_are_canonicalised_so_order_and_case_do_not_matter() {
        AnswerKey a = new AnswerKey(List.of("c", " A ", "A"), null, null, null);
        AnswerKey b = new AnswerKey(List.of("A", "C"), null, null, null);
        assertThat(a).isEqualTo(b);
        assertThat(a.options()).containsExactly("A", "C");
    }

    @Test
    void decimals_compare_by_value_not_scale() {
        AnswerKey a = new AnswerKey(null, new BigDecimal("2.50"), new BigDecimal("0.010"), null);
        AnswerKey b = new AnswerKey(null, new BigDecimal("2.5"), new BigDecimal("0.01"), null);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void zero_tolerance_is_the_same_as_no_tolerance() {
        assertThat(new AnswerKey(null, BigDecimal.TEN, BigDecimal.ZERO, null))
                .isEqualTo(new AnswerKey(null, BigDecimal.TEN, null, null));
    }

    @Test
    void json_round_trip_matches_seed_format() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AnswerKey fromSeed = mapper.readValue("{\"value\": 20, \"tolerance\": 0}", AnswerKey.class);
        assertThat(fromSeed.value()).isEqualByComparingTo("20");
        assertThat(fromSeed.tolerance()).isNull();

        AnswerKey match = new AnswerKey(null, null, null, Map.of("q", "1", "p", "2"));
        assertThat(mapper.writeValueAsString(match)).isEqualTo("{\"pairs\":{\"P\":\"2\",\"Q\":\"1\"}}");
        assertThat(mapper.writeValueAsString(AnswerKey.EMPTY)).isEqualTo("{}");
    }
}
