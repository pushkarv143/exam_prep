package com.examprep.question.model;

import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which texts of the primary language a translation does not cover yet. Used by the
 * editor ("Hindi: 2 fields missing") and later by the pre-publish checklist of tests.
 */
public final class TranslationCheck {

    private TranslationCheck() {
    }

    /** Paths of missing texts, e.g. {@code ["text", "options.C", "solution"]}; empty = complete. */
    public static List<String> missing(QuestionType type, QuestionContent c, QuestionTranslation t) {
        List<String> out = new ArrayList<>();
        QuestionTranslation tr = t == null ? new QuestionTranslation(null, null, null, null, null, null) : t;
        if (notBlank(c.text()) && tr.text() == null) {
            out.add("text");
        }
        if (type == QuestionType.PARAGRAPH && notBlank(c.paragraph()) && tr.paragraph() == null) {
            out.add("paragraph");
        }
        for (Option o : c.options()) {
            if (notBlank(o.text()) && !covered(tr.options(), o.id())) {
                out.add("options." + o.id().toUpperCase());
            }
        }
        for (MatchItem i : c.matchLeft()) {
            if (!covered(tr.matchLeft(), i.id())) {
                out.add("matchLeft." + i.id().toUpperCase());
            }
        }
        for (MatchItem i : c.matchRight()) {
            if (!covered(tr.matchRight(), i.id())) {
                out.add("matchRight." + i.id().toUpperCase());
            }
        }
        if (c.solution() != null && notBlank(c.solution().text()) && tr.solution() == null) {
            out.add("solution");
        }
        return out;
    }

    private static boolean covered(Map<String, String> texts, String id) {
        return texts.containsKey(id.trim().toUpperCase());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
