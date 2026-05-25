package com.travyn.trip.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class TripFullException extends BaseException {
    public TripFullException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
