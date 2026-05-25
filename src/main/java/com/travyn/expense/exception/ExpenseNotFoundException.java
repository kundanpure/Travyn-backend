package com.travyn.expense.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ExpenseNotFoundException extends BaseException {
    public ExpenseNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
