package com.examprep.common.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Custom SQL functions usable from JPQL/Criteria. Registered via
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 *
 * <p>{@code question_text_lower(content)} renders <em>exactly</em> the expression
 * indexed by {@code ix_questions_text_trgm} (V3). The JSON key is inlined, not
 * bound, so PostgreSQL can use the trigram GIN index for {@code LIKE '%term%'} searches.
 */
public class SqlFunctionsContributor implements FunctionContributor {

    public static final String QUESTION_TEXT_LOWER = "question_text_lower";
    /** {@code jsonb_has_key(json, key)}: true when the JSONB object has the top-level key. */
    public static final String JSONB_HAS_KEY = "jsonb_has_key";

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerPattern(
                QUESTION_TEXT_LOWER,
                "lower(jsonb_extract_path_text(?1, 'text'))",
                contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING));
        contributions.getFunctionRegistry().registerPattern(
                JSONB_HAS_KEY,
                "jsonb_exists(?1, ?2)",
                contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN));
    }
}
