package com.examprep.attempt.entity;

public enum SubmitType {
    /** The student clicked Submit. */
    MANUAL,
    /** Server-initiated: time expired, or the anti-cheat limit was exceeded. */
    AUTO
}
