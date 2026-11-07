package ru.mephi.stay.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorDto> business(ResponseStatusException ex, HttpServletRequest req) {
        return error(ex.getStatusCode().value(), ex.getReason(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorDto> invalidField(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var field = ex.getBindingResult().getFieldErrors().stream().findFirst();
        String message = field.map(f -> f.getField() + ": " + f.getDefaultMessage()).orElse("Проверьте поля запроса");
        return error(400, message, req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestValueException.class, HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ErrorDto> invalidInput(Exception ex, HttpServletRequest req) {
        return error(400, "Проверьте параметры и формат запроса", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorDto> conflict(HttpServletRequest req) {
        return error(409, "Запись уже существует или на неё ссылаются другие данные", req);
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ErrorDto> invalidRoute(Exception ex, HttpServletRequest req) {
        var details = (org.springframework.web.ErrorResponse) ex;
        return error(details.getStatusCode().value(), details.getBody().getDetail(), req);
    }

    private ResponseEntity<ErrorDto> error(int status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErrorDto.of(status, message, req.getRequestURI(), MDC.get("traceId")));
    }
}
