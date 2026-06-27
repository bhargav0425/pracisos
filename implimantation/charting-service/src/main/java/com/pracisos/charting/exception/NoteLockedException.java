package com.pracisos.charting.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NoteLockedException extends RuntimeException {
    public NoteLockedException(String message) {
        super(message);
    }
}
