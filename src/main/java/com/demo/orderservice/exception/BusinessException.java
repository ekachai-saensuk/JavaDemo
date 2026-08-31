package com.demo.orderservice.exception;

import lombok.Getter;

/**
 * ใช้ throw จาก Service Layer เมื่อข้อมูลไม่ผ่านกฎทางธุรกิจ (Business Rule)
 * เช่น จำนวนสินค้าไม่ถูกต้อง, สถานะ Order ไม่สามารถเปลี่ยนได้ ฯลฯ
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
