package com.fresveg.commerce.api;

import com.fresveg.commerce.application.CommerceException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class CommerceExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CommerceExceptionHandler.class);

    @ExceptionHandler(CommerceException.class)
    ProblemDetail commerce(CommerceException error, HttpServletRequest request) {
        return CommerceProblems.create(error.status(), error.code(), error.getMessage(), request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ProblemDetail concurrentUpdate(Exception error, HttpServletRequest request) {
        return CommerceProblems.create(HttpStatus.CONFLICT, "CRT-409-001", "The resource has changed; reload it before updating.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(Exception error, HttpServletRequest request) {
        return CommerceProblems.create(HttpStatus.CONFLICT, "CRT-409-003", "The request conflicts with commerce data.", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalid(Exception error, HttpServletRequest request) {
        return CommerceProblems.create(HttpStatus.BAD_REQUEST, "CRT-400-001", "Request validation failed.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(Exception error, HttpServletRequest request) {
        return CommerceProblems.create(HttpStatus.FORBIDDEN, "SEC-403-001", "Access to this resource is denied.", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var servletRequest = ((ServletWebRequest) request).getRequest();
        var problem = CommerceProblems.create(status, "CRT-" + status.value() + "-001",
                status.value() == 400 ? "Request validation failed." : HttpStatus.valueOf(status.value()).getReasonPhrase(), servletRequest);
        return new ResponseEntity<>(problem, headers, status);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception error, HttpServletRequest request) {
        // Do not log token claims, request bodies, SQL parameters or personal data.
        LOG.error("Unhandled commerce failure ({})", error.getClass().getSimpleName());
        return CommerceProblems.create(HttpStatus.INTERNAL_SERVER_ERROR, "CRT-500-001", "An unexpected error occurred.", request);
    }
}
