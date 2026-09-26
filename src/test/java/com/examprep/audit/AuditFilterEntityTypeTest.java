package com.examprep.audit;

import com.examprep.audit.web.AuditFilter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFilterEntityTypeTest {

    private static Object type(String path) throws Exception {
        Method m = AuditFilter.class.getDeclaredMethod("entityType", String.class);
        m.setAccessible(true);
        return m.invoke(null, path);
    }

    @Test
    void derives_singular_entity_types_from_admin_paths() throws Exception {
        assertThat(type("/api/v1/admin/tests/123/publish")).isEqualTo("TEST");
        assertThat(type("/api/v1/admin/questions")).isEqualTo("QUESTION");
        assertThat(type("/api/v1/admin/batches/1/members")).isEqualTo("BATCH");
        assertThat(type("/api/v1/admin/series/1")).isEqualTo("SERIES");
        assertThat(type("/api/v1/admin/approval-policies/test.publish")).isEqualTo("APPROVAL_POLICY");
        assertThat(type("/api/v1/admin/approvals/1/approve")).isEqualTo("APPROVAL_REQUEST");
        assertThat(type("/api/v1/me/mfa")).isNull();
    }
}
