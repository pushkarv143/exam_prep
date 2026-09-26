package com.examprep.question.workflow;

import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Port implemented by the test module: moves the version pins of tests that use a question
 * after a new version of it is published. The question module must not depend on the test
 * module (the dependency runs the other way), so it talks to tests only through this interface.
 */
public interface TestPinUpdater {

    record Result(int draftTestsUpdated, int liveTestsUpdated, int liveTestsKept) {
        public static final Result NONE = new Result(0, 0, 0);
    }

    /**
     * Draft tests always move to {@code newVersion}. Published tests move only when
     * {@code includeLive} is set and {@code compatible} accepts the version they pin
     * (for a question: same scoring; a passage has no answer key, so any version).
     *
     * @param paragraph true when {@code questionId} is a PARAGRAPH: its children's passage pins move
     */
    Result moveToVersion(UUID questionId, int newVersion, boolean paragraph, boolean includeLive,
                         IntPredicate compatible);
}
