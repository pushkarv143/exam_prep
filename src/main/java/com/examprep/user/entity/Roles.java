package com.examprep.user.entity;

import java.util.Set;

/** Codes of the built-in roles. Custom roles created from the UI are plain strings too. */
public final class Roles {

    public static final String STUDENT = "STUDENT";
    public static final String TEACHER = "TEACHER";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String CONTENT_MANAGER = "CONTENT_MANAGER";
    public static final String REVIEWER = "REVIEWER";
    public static final String TEST_OPERATOR = "TEST_OPERATOR";
    public static final String SUPPORT_AGENT = "SUPPORT_AGENT";
    public static final String FINANCE = "FINANCE";
    public static final String MARKETING = "MARKETING";

    public static final Set<String> BUILT_IN = Set.of(STUDENT, TEACHER, SUPER_ADMIN, CONTENT_MANAGER, REVIEWER,
            TEST_OPERATOR, SUPPORT_AGENT, FINANCE, MARKETING);

    /** Any role other than STUDENT is a staff role. */
    public static boolean isStaff(Set<String> roles) {
        return roles.stream().anyMatch(r -> !STUDENT.equals(r));
    }

    private Roles() {
    }
}
