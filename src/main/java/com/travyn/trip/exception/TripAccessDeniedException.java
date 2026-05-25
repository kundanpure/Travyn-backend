package com.travyn.trip.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class TripAccessDeniedException extends BaseException {
    public TripAccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
