package com.examprep.question.model;

import com.examprep.question.entity.Language;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Translations of a question keyed by language, stored in {@code questions.translations}
 * as {@code {"HI": {...}}}. A wrapper type (rather than a raw Map) keeps the generic
 * value type intact when Hibernate maps the JSONB column. Empty translations and the
 * question's own primary language are dropped by {@link #without(Language)}.
 */
public final class QuestionTranslations {

    public static final QuestionTranslations NONE = new QuestionTranslations(Map.of());

    private final Map<Language, QuestionTranslation> byLanguage;

    private QuestionTranslations(Map<Language, QuestionTranslation> byLanguage) {
        this.byLanguage = byLanguage;
    }

    @JsonCreator
    public static QuestionTranslations of(Map<Language, QuestionTranslation> map) {
        if (map == null || map.isEmpty()) {
            return NONE;
        }
        EnumMap<Language, QuestionTranslation> copy = new EnumMap<>(Language.class);
        map.forEach((lang, t) -> {
            if (lang != null && t != null && !t.hasNoText()) {
                copy.put(lang, t);
            }
        });
        return copy.isEmpty() ? NONE : new QuestionTranslations(Collections.unmodifiableMap(copy));
    }

    @JsonValue
    public Map<Language, QuestionTranslation> asMap() {
        return byLanguage;
    }

    public Optional<QuestionTranslation> get(Language language) {
        return Optional.ofNullable(byLanguage.get(language));
    }

    /** These translations minus the given (primary) language. */
    public QuestionTranslations without(Language primary) {
        if (!byLanguage.containsKey(primary)) {
            return this;
        }
        EnumMap<Language, QuestionTranslation> copy = new EnumMap<>(byLanguage);
        copy.remove(primary);
        return of(copy);
    }

    public boolean isEmpty() {
        return byLanguage.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof QuestionTranslations other && byLanguage.equals(other.byLanguage);
    }

    @Override
    public int hashCode() {
        return byLanguage.hashCode();
    }

    @Override
    public String toString() {
        return byLanguage.toString();
    }
}
