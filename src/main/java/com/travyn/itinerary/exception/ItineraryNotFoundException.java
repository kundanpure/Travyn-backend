package com.travyn.itinerary.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ItineraryNotFoundException extends BaseException {
    public ItineraryNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
