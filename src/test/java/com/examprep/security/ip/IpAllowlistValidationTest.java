package com.examprep.security.ip;

import com.examprep.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpAllowlistValidationTest {

    @Test
    void normalises_single_addresses_to_host_ranges() {
        assertThat(IpAllowlistService.validate("203.0.113.7")).isEqualTo("203.0.113.7/32");
        assertThat(IpAllowlistService.validate(" 10.0.0.0/8 ")).isEqualTo("10.0.0.0/8");
        assertThat(IpAllowlistService.validate("2001:db8::1")).isEqualTo("2001:db8::1/128");
    }

    @Test
    void rejects_garbage() {
        assertThatThrownBy(() -> IpAllowlistService.validate("my-office")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> IpAllowlistService.validate("300.1.1.1")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> IpAllowlistService.validate("")).isInstanceOf(BusinessException.class);
    }
}
