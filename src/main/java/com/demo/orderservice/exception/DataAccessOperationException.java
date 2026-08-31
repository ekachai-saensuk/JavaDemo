package com.demo.orderservice.exception;

/**
 * ใช้ครอบ (wrap) exception ที่เกิดจากชั้น Repository เวลาเรียก Stored Procedure ล้มเหลว
 * (เช่น SQLException, DataAccessException จาก Spring JDBC)
 * เพื่อไม่ให้รายละเอียดของ Database หลุดขึ้นไปยัง Layer บนโดยตรง
 */
public class DataAccessOperationException extends RuntimeException {

    public DataAccessOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessOperationException(String message) {
        super(message);
    }
}
