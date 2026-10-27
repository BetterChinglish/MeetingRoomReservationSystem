package com.exam.common.exception;

import com.exam.common.result.ApiResponse;
import com.exam.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        log.warn("Business Exception [code={}]: {}", e.getCode(), e.getMessage());
        HttpStatus status;
        try {
            status = HttpStatus.valueOf(e.getCode());
        } catch (IllegalArgumentException ex) {
            status = HttpStatus.BAD_REQUEST;
        }
        return new ResponseEntity<>(ApiResponse.error(e.getCode(), e.getMessage(), e.getData()), status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation Error: {}", errorMsg);
        return new ResponseEntity<>(
                ApiResponse.error(ResultCode.BAD_REQUEST.getCode(), errorMsg),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return new ResponseEntity<>(
                ApiResponse.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("Internal Server Error: ", e);
        return new ResponseEntity<>(
                ApiResponse.error(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "系统内部异常，请稍后再试"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
