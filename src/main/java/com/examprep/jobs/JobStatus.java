package com.examprep.jobs;

public enum JobStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, DEAD;

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == DEAD;
    }
}
