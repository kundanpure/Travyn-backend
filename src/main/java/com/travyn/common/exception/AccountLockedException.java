package com.travyn.common.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends BaseException {
    public AccountLockedException(String message) {
        super(message, HttpStatus.LOCKED);
    }
}
