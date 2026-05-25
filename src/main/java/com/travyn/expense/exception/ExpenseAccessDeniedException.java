package com.travyn.expense.exception;

import com.travyn.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ExpenseAccessDeniedException extends BaseException {
    public ExpenseAccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
