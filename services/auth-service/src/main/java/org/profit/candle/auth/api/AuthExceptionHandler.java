package org.profit.candle.auth.api;

import org.profit.candle.auth.exception.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<ErrorResponse> handle(AuthException exception) {
        return ResponseEntity.status(exception.errorCode().httpStatus())
                .body(new ErrorResponse(exception.errorCode().code(), exception.errorCode().message()));
    }

    record ErrorResponse(String code, String message) {
    }
}
