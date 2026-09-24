package com.examprep.auth.dto;

/** A single definition of the password policy, reused by every request that sets a password. */
public final class PasswordPolicy {

    /** 8-72 chars (72 = bcrypt input limit), at least one letter and one digit. */
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MESSAGE = "must be 8-72 characters and contain at least one letter and one digit";

    private PasswordPolicy() {
    }
}
