package com.examprep.approval;

import java.util.concurrent.Callable;

/** Marks "we are executing approved request X" on the current thread, so the guard lets it through. */
public final class ApprovalExecution {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ApprovalExecution() {
    }

    static <T> T run(String action, String entityId, Callable<T> body) throws Exception {
        String previous = CURRENT.get();
        CURRENT.set(key(action, entityId));
        try {
            return body.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static boolean isExecuting(String action, Object entityId) {
        return key(action, entityId == null ? null : entityId.toString()).equals(CURRENT.get());
    }

    private static String key(String action, String entityId) {
        return action + "|" + entityId;
    }
}
