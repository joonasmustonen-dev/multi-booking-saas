package com.example.booking.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handle(
        Exception exception,
        HttpServletRequest request
    ) {
        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;

        String detail =
            "The request could not be completed. Contact support with the request ID.";

        if (exception instanceof ResponseStatusException rejected) {
            status = rejected.getStatusCode();

            detail =
                rejected.getReason() == null
                    ? "Request rejected"
                    : rejected.getReason();
        } else if (exception instanceof AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;

            detail = "You do not have permission to perform this action";
        } else if (exception instanceof MethodArgumentNotValidException) {
            status = HttpStatus.BAD_REQUEST;

            detail =
                "Some fields are invalid. Check required fields, formats and length limits.";
        } else if (
            exception instanceof HttpMessageNotReadableException ||
            exception instanceof
                org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
        ) {
            status = HttpStatus.BAD_REQUEST;

            detail =
                "Invalid request. Check the supplied fields and parameters.";
        } else if (exception instanceof DataIntegrityViolationException) {
            status = HttpStatus.CONFLICT;

            detail = "This change conflicts with an existing record";
        } else if (
            exception instanceof org.springframework.web.ErrorResponse error
        ) {
            status = error.getStatusCode();

            detail =
                "Invalid request. Check the supplied fields and parameters.";
        }

        for (
            Throwable cause = exception;
            cause != null;
            cause = cause.getCause()
        ) {
            if (cause instanceof ApiSafetyFilter.PayloadTooLarge) {
                status = HttpStatus.PAYLOAD_TOO_LARGE;

                detail = "Request body is too large";

                break;
            }
        }

        if (status.is5xxServerError()) {
            LoggerFactory.getLogger(ApiErrorHandler.class).error(
                "Request {} failed ({})",
                request.getAttribute("correlationId"),
                exception.getClass().getSimpleName()
            );
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            status,
            detail
        );

        problem.setProperty("requestId", request.getAttribute("correlationId"));

        return ResponseEntity.status(status).body(problem);
    }
}
