package com.demo.orderservice.controller;

import com.demo.orderservice.dto.*;
import com.demo.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller Layer
 * - รับ/ส่ง HTTP Request-Response
 * - ทำหน้าที่แปลง DTO เข้า-ออกเท่านั้น ไม่มี Business Logic อยู่ในชั้นนี้
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs สำหรับจัดการคำสั่งซื้อสินค้า (Demo: Stored Procedure based)")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "สร้างคำสั่งซื้อใหม่ (Create Order)")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(
            @Valid @RequestBody CreateOrderRequestDto request) {

        log.info("Received request to create order for customer [{}]", request.getCustomerName());

        OrderResponseDto created = orderService.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Order created successfully"));
    }

    @Operation(summary = "ดึงข้อมูลคำสั่งซื้อตาม OrderId")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrderById(
            @PathVariable Long orderId) {

        OrderResponseDto order = orderService.getOrderById(orderId);

        return ResponseEntity.ok(ApiResponse.success(order, "Order retrieved successfully"));
    }

    @Operation(summary = "ดึงรายการคำสั่งซื้อทั้งหมด พร้อม Filter สถานะ และ Paging")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponseDto<OrderResponseDto>>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {

        PagedResponseDto<OrderResponseDto> orders = orderService.getOrders(status, pageNumber, pageSize);

        return ResponseEntity.ok(ApiResponse.success(orders, "Orders retrieved successfully"));
    }

    @Operation(summary = "อัปเดตสถานะคำสั่งซื้อ (PENDING / CONFIRMED / CANCELLED)")
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponseDto>> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequestDto request) {

        OrderResponseDto updated = orderService.updateOrderStatus(orderId, request.getStatus());

        return ResponseEntity.ok(ApiResponse.success(updated, "Order status updated successfully"));
    }
}
