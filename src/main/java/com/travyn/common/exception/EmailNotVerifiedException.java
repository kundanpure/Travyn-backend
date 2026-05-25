package com.travyn.common.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends BaseException {
    public EmailNotVerifiedException() {
        super("Please verify your email before logging in", HttpStatus.FORBIDDEN);
    }
}
