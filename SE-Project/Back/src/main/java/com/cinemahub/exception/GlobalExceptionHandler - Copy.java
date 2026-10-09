package com.cinemahub.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Catches exceptions that bubble up from controllers so that users never see
 * a raw Java stack trace - they always land on one of the friendly pages in
 * templates/error/.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    /**
     * Spring throws this (instead of quietly 404ing) for any request that
     * doesn't match a route - e.g. a stale/mistyped URL. Without this handler
     * it fell through to {@link #handleGeneric} below and showed a 500 page.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoRouteMatched(NoResourceFoundException ex, Model model) {
        model.addAttribute("message", "Page not found.");
        return "error/404";
    }

    /** Missing/garbled request parameters are the client's mistake (400), not a server fault. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(Exception ex, Model model) {
        model.addAttribute("message", "That request was incomplete or invalid. Please go back and try again.");
        return "error/400";
    }

    /** A receipt upload (or any future multipart form) over spring.servlet.multipart.max-file-size (5MB). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String handleUploadTooLarge(Model model) {
        model.addAttribute("message", "That file is too large - the limit is 5MB.");
        return "error/400";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String handleWrongMethod(HttpRequestMethodNotSupportedException ex, Model model) {
        model.addAttribute("message", "That action isn't available this way.");
        return "error/400";
    }

    /**
     * A signed-in user calling something their role isn't allowed to (e.g. a Manager opening
     * /manager/payments, which is System Admin only - see @PreAuthorize on ManagerPaymentController).
     * Without this it fell through to {@link #handleGeneric} and showed a 500 page.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied() {
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return "error/500";
    }
}
