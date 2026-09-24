package com.examprep.attempt.entity;

/** Proctoring and anti-cheat signals. Client-reported ones are advisory; server ones are authoritative. */
public enum AttemptEventType {
    // client-reported
    TAB_SWITCH,
    WINDOW_BLUR,
    FULLSCREEN_EXIT,
    FULLSCREEN_ENTER,
    COPY,
    PASTE,
    CONTEXT_MENU,
    DEVTOOLS_OPEN,
    NETWORK_OFFLINE,
    NETWORK_ONLINE,
    // server-recorded
    STARTED,
    RESUMED,
    SUBMITTED,
    AUTO_SUBMITTED;

    public boolean isClientReportable() {
        return ordinal() < STARTED.ordinal();
    }
}
