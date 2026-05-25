package com.travyn.trip.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AlreadyMemberException extends BaseException {
    public AlreadyMemberException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
