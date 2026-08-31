package com.demo.orderservice.exception;

/**
 * ใช้ throw เมื่อไม่พบข้อมูลที่ร้องขอ เช่น Order ไม่พบตาม OrderId
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
