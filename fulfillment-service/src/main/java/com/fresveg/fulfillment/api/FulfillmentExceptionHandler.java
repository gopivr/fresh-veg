package com.fresveg.fulfillment.api;
import com.fresveg.fulfillment.application.FulfillmentException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class FulfillmentExceptionHandler {
 private static final Logger LOG=LoggerFactory.getLogger(FulfillmentExceptionHandler.class);
 @ExceptionHandler(FulfillmentException.class) ProblemDetail domain(FulfillmentException e,HttpServletRequest r){return FulfillmentProblems.create(e.status(),e.code(),e.getMessage(),r);}
 @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,IllegalArgumentException.class}) ProblemDetail bad(Exception e,HttpServletRequest r){return FulfillmentProblems.create(HttpStatus.BAD_REQUEST,"FUL-400-001","Invalid fulfillment request.",r);}
 @ExceptionHandler(AccessDeniedException.class) ProblemDetail denied(Exception e,HttpServletRequest r){return FulfillmentProblems.create(HttpStatus.FORBIDDEN,"SEC-403-001","Access to this resource is denied.",r);}
 @ExceptionHandler(NoResourceFoundException.class) ProblemDetail missing(Exception e,HttpServletRequest r){return FulfillmentProblems.create(HttpStatus.NOT_FOUND,"FUL-404-001","Fulfillment resource not found.",r);}
 @ExceptionHandler(Exception.class) ProblemDetail unexpected(Exception e,HttpServletRequest r){LOG.warn("Fulfillment request failed ({})",e.getClass().getSimpleName());return FulfillmentProblems.create(HttpStatus.INTERNAL_SERVER_ERROR,"FUL-500-001","Unexpected fulfillment failure.",r);}
}
