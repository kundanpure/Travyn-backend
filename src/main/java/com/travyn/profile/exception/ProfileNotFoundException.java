package com.travyn.profile.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ProfileNotFoundException extends BaseException {
    public ProfileNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
