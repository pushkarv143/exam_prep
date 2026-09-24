package com.examprep.security.jwt;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(ErrorCode code) {
        super(code);
    }

    public InvalidTokenException(ErrorCode code, String message) {
        super(code, message);
    }
}
