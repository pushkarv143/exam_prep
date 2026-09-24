package com.examprep.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidsTest {

    @Test
    void generates_rfc9562_version7_variant2() {
        UUID id = Uuids.v7();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embeds_current_millisecond_timestamp_and_sorts_by_time() throws InterruptedException {
        long before = System.currentTimeMillis();
        UUID first = Uuids.v7();
        Thread.sleep(2);
        UUID second = Uuids.v7();

        long embedded = first.getMostSignificantBits() >>> 16;
        assertThat(embedded).isBetween(before, System.currentTimeMillis());
        // Later ids compare greater (as unsigned MSB), so B-tree inserts are append-mostly.
        assertThat(Long.compareUnsigned(second.getMostSignificantBits(), first.getMostSignificantBits()))
                .isPositive();
    }

    @Test
    void is_unique_under_burst() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(Uuids.v7());
        }
        assertThat(ids).hasSize(100_000);
    }
}
