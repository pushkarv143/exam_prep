package com.examprep.approval;

/**
 * Thrown by {@link MakerChecker#guard} when the operation must wait for a second person.
 * The request is already stored. The global handler turns this into HTTP 202 with an
 * {@link ApprovalDtos.ApprovalPending} body, and the calling transaction rolls back (nothing was done).
 */
public class ApprovalRequiredException extends RuntimeException {

    private final transient ApprovalDtos.ApprovalRequestDto request;
    private final boolean alreadyPending;

    public ApprovalRequiredException(ApprovalDtos.ApprovalRequestDto request, boolean alreadyPending) {
        super(alreadyPending ? "This action is already waiting for approval" : "Sent for approval");
        this.request = request;
        this.alreadyPending = alreadyPending;
    }

    public ApprovalDtos.ApprovalRequestDto getRequest() {
        return request;
    }

    public boolean isAlreadyPending() {
        return alreadyPending;
    }
}
