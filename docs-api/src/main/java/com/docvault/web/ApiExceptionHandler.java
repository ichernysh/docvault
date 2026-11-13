package com.docvault.web;

import com.docvault.document.DocumentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** AccessDeniedException is deliberately not handled here: Spring Security maps it to 403. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound(DocumentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
