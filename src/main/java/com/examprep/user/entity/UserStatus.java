package com.examprep.user.entity;

public enum UserStatus {
    ACTIVE,
    /** Deactivated by an admin; cannot log in. */
    INACTIVE,
    /** Temporarily locked (e.g. suspected abuse). */
    LOCKED
}
