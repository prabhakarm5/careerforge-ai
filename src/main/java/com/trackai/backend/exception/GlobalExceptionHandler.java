package com.trackai.backend.exception;

import com.trackai.backend.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

        // Helper to build consistent error response
        private ErrorResponse buildResponse(
                        HttpStatus status,
                        String error,
                        String message,
                        HttpServletRequest request) {

                return ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(status.value())
                                .error(error)
                                .message(message)
                                .path(request.getRequestURI())
                                .build();
        }

        // VALIDATION ERRORS -> 400
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationExceptions(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {

                Map<String, String> validationErrors = new HashMap<>();

                ex.getBindingResult()
                                .getAllErrors()
                                .forEach(error -> {

                                        String fieldName = ((FieldError) error).getField();
                                        String errorMessage = error.getDefaultMessage();

                                        validationErrors.put(fieldName, errorMessage);
                                });

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("Validation Failed")
                                .message("Invalid request data")
                                .path(request.getRequestURI())
                                .validationErrors(validationErrors)
                                .build();

                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // RESOURCE NOT FOUND -> 404
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleResourceNotFound(
                        ResourceNotFoundException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request),
                                HttpStatus.NOT_FOUND);
        }

        // INVALID CREDENTIALS -> 401
        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleInvalidCredentials(
                        InvalidCredentialsException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request),
                                HttpStatus.UNAUTHORIZED);
        }

        // ACCOUNT BLOCKED -> 403
        @ExceptionHandler(AccountBlockedException.class)
        public ResponseEntity<ErrorResponse> handleAccountBlocked(
                        AccountBlockedException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.FORBIDDEN, "Account Blocked", ex.getMessage(), request),
                                HttpStatus.FORBIDDEN);
        }

        // ACCOUNT DISABLED -> 403
        @ExceptionHandler(AccountDisabledException.class)
        public ResponseEntity<ErrorResponse> handleAccountDisabled(
                        AccountDisabledException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.FORBIDDEN, "Account Disabled", ex.getMessage(), request),
                                HttpStatus.FORBIDDEN);
        }

        // OTP ERRORS -> 400
        @ExceptionHandler(OtpException.class)
        public ResponseEntity<ErrorResponse> handleOtpException(
                        OtpException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.BAD_REQUEST, "OTP Error", ex.getMessage(), request),
                                HttpStatus.BAD_REQUEST);
        }

        // RATE LIMIT EXCEEDED -> 429
        @ExceptionHandler(RateLimitExceededException.class)
        public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
                        RateLimitExceededException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.TOO_MANY_REQUESTS, "Rate Limit Exceeded", ex.getMessage(),
                                                request),
                                HttpStatus.TOO_MANY_REQUESTS);
        }

        @ExceptionHandler(OpenRouterException.class)
        public ResponseEntity<ErrorResponse> handleOpenRouterException(
                        OpenRouterException ex,
                        HttpServletRequest request) {

                String message = sanitizeOpenRouterMessage(ex.getMessage());

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.BAD_GATEWAY, "AI Provider Error", message, request),
                                HttpStatus.BAD_GATEWAY);
        }

        private String sanitizeOpenRouterMessage(String rawMessage) {
                if (rawMessage == null || rawMessage.isBlank()) {
                        return "The AI provider could not complete the request. Please try again.";
                }

                String lower = rawMessage.toLowerCase();

                if (lower.contains("insufficient credits") || lower.contains("\"code\":402")) {
                        return "OpenRouter does not have enough credits for this model. Please choose a free model or add credits.";
                }

                if (lower.contains("invalid") && lower.contains("api")) {
                        return "OpenRouter API key is invalid or not active.";
                }

                if (lower.contains("model") && (lower.contains("not found") || lower.contains("not available"))) {
                        return "The selected OpenRouter model is not available. Please choose another model.";
                }

                if (lower.contains("rate") || lower.contains("429")) {
                        return "OpenRouter is rate-limiting this model. Please try again or choose another model.";
                }

                return "OpenRouter could not complete the request. Please try again or choose another model.";
        }

        // ACCESS DENIED (Spring Security)
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDeniedException(
                        AccessDeniedException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.FORBIDDEN, "Access Denied",
                                                "You are not authorized to access this resource", request),
                                HttpStatus.FORBIDDEN);
        }

        // FALLBACK: any other RuntimeException not specifically handled above
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(
                        RuntimeException ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request),
                                HttpStatus.BAD_REQUEST);
        }

        // GENERAL EXCEPTION (anything non-Runtime, unexpected)
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleException(
                        Exception ex,
                        HttpServletRequest request) {

                return new ResponseEntity<>(
                                buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                                                "Something went wrong", request),
                                HttpStatus.INTERNAL_SERVER_ERROR);
        }
}
