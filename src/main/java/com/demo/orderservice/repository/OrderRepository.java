package com.demo.orderservice.repository;

import com.demo.orderservice.entity.Order;

import java.util.List;
import java.util.Optional;

/**
 * Repository Layer Contract
 * ทุก method ในนี้ ภายในจะ Execute ผ่าน Stored Procedure เท่านั้น (ไม่ใช้ JPA/ORM query)
 */
public interface OrderRepository {

    /**
     * เรียก sp_CreateOrder เพื่อสร้าง Order ใหม่พร้อมรายการสินค้า
     * @return orderId ที่เพิ่งสร้าง (รับค่าจาก OUTPUT parameter ของ SP)
     */
    Long createOrder(Order order);

    /**
     * เรียก sp_GetOrderById ซึ่งคืน 2 result sets (header + items) แล้ว map รวมเป็น Order เดียว
     */
    Optional<Order> findById(Long orderId);

    /**
     * เรียก sp_GetOrderList พร้อม paging และคืนจำนวนทั้งหมดผ่าน OUTPUT parameter
     */
    OrderPage findAll(String status, int pageNumber, int pageSize);

    /**
     * เรียก sp_UpdateOrderStatus และคืนจำนวนแถวที่ถูกอัปเดต (OUTPUT parameter)
     */
    int updateStatus(Long orderId, String newStatus);

    /**
     * Value object ง่ายๆ ใช้ส่งผลลัพธ์ paging กลับจาก Repository Layer
     */
    record OrderPage(List<Order> content, int totalCount) {}
}
