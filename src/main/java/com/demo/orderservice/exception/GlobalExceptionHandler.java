package com.demo.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * รวมจุดจัดการ Exception ของทั้งแอปพลิเคชัน (Controller Layer)
 * แปลง Exception จากทุก Layer ให้เป็น HTTP Response ที่มีรูปแบบสม่ำเสมอ
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 1) Validation error จาก @Valid ที่ DTO (Controller Layer) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        log.warn("Validation failed on [{}]: {}", request.getRequestURI(), details);

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .errorCode("VALIDATION_ERROR")
                .message("Request payload is invalid")
                .details(details)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /** 2) ไม่พบข้อมูล (Service Layer) */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .errorCode("RESOURCE_NOT_FOUND")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /** 3) ผิดกฎ Business Logic (Service Layer) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        log.warn("Business rule violation on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /** 4) Exception ที่ถูกครอบมาจาก Repository Layer (เรียก Stored Procedure ไม่สำเร็จ) */
    @ExceptionHandler(DataAccessOperationException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessOperationException(
            DataAccessOperationException ex, HttpServletRequest request) {

        log.error("Data access operation failed on [{}]", request.getRequestURI(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .errorCode("DATABASE_ERROR")
                .message("A database error occurred while processing your request")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /** 5) เผื่อกรณี DataAccessException หลุดขึ้นมาโดยไม่ได้ถูกครอบใน Repository */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleSpringDataAccessException(
            DataAccessException ex, HttpServletRequest request) {

        log.error("Unhandled Spring DataAccessException on [{}]", request.getRequestURI(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .errorCode("DATABASE_ERROR")
                .message("A database error occurred while processing your request")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /** 6) Fallback: จับทุกอย่างที่เหลือ ไม่ให้ stack trace หลุดไปหา client */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error on [{}]", request.getRequestURI(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please contact support.")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
