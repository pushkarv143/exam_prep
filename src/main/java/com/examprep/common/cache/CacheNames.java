package com.examprep.common.cache;

/** Every Redis cache name lives here. Each cache must be registered with a typed serializer in {@link CacheConfig}. */
public final class CacheNames {

    /** Active exams list (key: 'all'). */
    public static final String CATALOG_EXAMS = "catalog-exams";
    /** Active subject/chapter/topic tree per exam (key: exam code). */
    public static final String CATALOG_TREE = "catalog-tree";

    private CacheNames() {
    }
}
