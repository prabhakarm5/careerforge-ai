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

        // VALIDATION ERRORS
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse>

                        handleValidationExceptions(

                                        MethodArgumentNotValidException ex,

                                        HttpServletRequest request) {

                Map<String, String> validationErrors = new HashMap<>();

                ex.getBindingResult()

                                .getAllErrors()

                                .forEach(error -> {

                                        String fieldName =

                                                        ((FieldError) error)
                                                                        .getField();

                                        String errorMessage = error.getDefaultMessage();

                                        validationErrors.put(
                                                        fieldName,
                                                        errorMessage);
                                });

                ErrorResponse response =

                                ErrorResponse.builder()

                                                .timestamp(
                                                                LocalDateTime.now())

                                                .status(
                                                                HttpStatus.BAD_REQUEST.value())

                                                .error(
                                                                "Validation Failed")

                                                .message(
                                                                "Invalid request data")

                                                .path(
                                                                request.getRequestURI())

                                                .validationErrors(
                                                                validationErrors)

                                                .build();

                return new ResponseEntity<>(

                                response,

                                HttpStatus.BAD_REQUEST);
        }

        // RUNTIME EXCEPTIONS
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse>

                        handleRuntimeException(

                                        RuntimeException ex,

                                        HttpServletRequest request) {

                ErrorResponse response =

                                ErrorResponse.builder()

                                                .timestamp(
                                                                LocalDateTime.now())

                                                .status(
                                                                HttpStatus.BAD_REQUEST.value())

                                                .error(
                                                                "Bad Request")

                                                .message(
                                                                ex.getMessage())

                                                .path(
                                                                request.getRequestURI())

                                                .build();

                return new ResponseEntity<>(

                                response,

                                HttpStatus.BAD_REQUEST);
        }

        // ACCESS DENIED
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse>

                        handleAccessDeniedException(

                                        AccessDeniedException ex,

                                        HttpServletRequest request) {

                ErrorResponse response =

                                ErrorResponse.builder()

                                                .timestamp(
                                                                LocalDateTime.now())

                                                .status(
                                                                HttpStatus.FORBIDDEN.value())

                                                .error(
                                                                "Access Denied")

                                                .message(
                                                                "You are not authorized to access this resource")

                                                .path(
                                                                request.getRequestURI())

                                                .build();

                return new ResponseEntity<>(

                                response,

                                HttpStatus.FORBIDDEN);
        }

        // GENERAL EXCEPTION
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse>

                        handleException(

                                        Exception ex,

                                        HttpServletRequest request) {

                ErrorResponse response =

                                ErrorResponse.builder()

                                                .timestamp(
                                                                LocalDateTime.now())

                                                .status(
                                                                HttpStatus.INTERNAL_SERVER_ERROR.value())

                                                .error(
                                                                "Internal Server Error")

                                                .message(
                                                                "Something went wrong")

                                                .path(
                                                                request.getRequestURI())

                                                .build();

                return new ResponseEntity<>(

                                response,

                                HttpStatus.INTERNAL_SERVER_ERROR);
        }
}