package com.travyn.trip.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class TripNotFoundException extends BaseException {
    public TripNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
