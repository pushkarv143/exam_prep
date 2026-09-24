package com.examprep.test.entity;

import com.examprep.question.entity.QuestionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExamPatternTest {

    @Test
    void jee_main_is_90_questions_300_marks_180_minutes() {
        ExamPattern p = ExamPattern.JEE_MAIN;
        assertThat(p.totalQuestions()).isEqualTo(90);
        assertThat(p.totalMarks()).isEqualByComparingTo("300");
        assertThat(p.getDefaultDurationMinutes()).isEqualTo(180);
        assertThat(p.getSections()).hasSize(6);
        assertThat(p.getSections()).filteredOn(s -> s.type() == QuestionType.NUMERICAL)
                .allSatisfy(s -> {
                    assertThat(s.negativeMarks()).isEqualByComparingTo(BigDecimal.ZERO);   // no negative for numerical
                    assertThat(s.maxAttempt()).isEqualTo(5);                            // attempt any 5 of 10
                });
    }

    @Test
    void neet_is_180_questions_720_marks_200_minutes() {
        ExamPattern p = ExamPattern.NEET;
        assertThat(p.totalQuestions()).isEqualTo(180);
        assertThat(p.totalMarks()).isEqualByComparingTo("720");
        assertThat(p.getDefaultDurationMinutes()).isEqualTo(200);
        assertThat(p.getSections()).extracting(ExamPattern.SectionTemplate::subjectCode)
                .containsExactly("PHY", "CHEM", "BOT", "ZOO");
    }

    @Test
    void custom_has_no_sections_or_default_duration() {
        assertThat(ExamPattern.CUSTOM.getSections()).isEmpty();
        assertThat(ExamPattern.CUSTOM.getDefaultDurationMinutes()).isNull();
    }

    @Test
    void availability_follows_status_and_window() {
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        com.examprep.test.entity.Test t = new com.examprep.test.entity.Test();

        t.setStatus(TestStatus.DRAFT);
        assertThat(TestAvailability.of(t, now)).isEqualTo(TestAvailability.NOT_PUBLISHED);

        t.setStatus(TestStatus.PUBLISHED);
        assertThat(TestAvailability.of(t, now)).isEqualTo(TestAvailability.OPEN);            // no window

        t.setStartAt(now.plusSeconds(60));
        assertThat(TestAvailability.of(t, now)).isEqualTo(TestAvailability.UPCOMING);

        t.setStartAt(now.minusSeconds(60));
        t.setEndAt(now);                                                                     // end is exclusive
        assertThat(TestAvailability.of(t, now)).isEqualTo(TestAvailability.CLOSED);

        t.setStatus(TestStatus.COMPLETED);
        t.setEndAt(null);
        assertThat(TestAvailability.of(t, now)).isEqualTo(TestAvailability.CLOSED);
    }

    @Test
    void series_access_expiry_is_the_earlier_of_validity_days_and_valid_until() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TestSeries s = new TestSeries();
        assertThat(s.accessExpiry(now)).isNull();                                            // forever

        s.setValidityDays(30);
        assertThat(s.accessExpiry(now)).isEqualTo(Instant.parse("2026-01-31T00:00:00Z"));

        s.setValidUntil(Instant.parse("2026-01-10T00:00:00Z"));
        assertThat(s.accessExpiry(now)).isEqualTo(Instant.parse("2026-01-10T00:00:00Z"));

        s.setValidityDays(null);
        assertThat(s.accessExpiry(now)).isEqualTo(Instant.parse("2026-01-10T00:00:00Z"));
    }
}
