package com.examprep.question.entity;

/** Where a question comes from. PYQ questions also carry the year and shift. */
public enum SourceType {
    /** Previous-year question of a real exam. */
    PYQ,
    /** From a coaching institute's material. */
    COACHING,
    /** From a textbook or reference book. */
    BOOK,
    /** Written in-house. */
    ORIGINAL
}
