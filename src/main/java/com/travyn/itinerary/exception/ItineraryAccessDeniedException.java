package com.travyn.itinerary.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ItineraryAccessDeniedException extends BaseException {
    public ItineraryAccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
