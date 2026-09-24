package com.examprep.common.exception;

/** Thrown when an entity addressed by id/slug does not exist (or is not visible to the caller). */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " not found: " + id);
    }
}
