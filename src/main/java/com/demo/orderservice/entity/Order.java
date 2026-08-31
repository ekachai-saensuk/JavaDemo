package com.demo.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain Entity: แทนโครงสร้างข้อมูล 1 แถวในตาราง dbo.Orders
 * หมายเหตุ: ไม่ใช้ JPA @Entity เพราะ Repository Layer เรียกผ่าน Stored Procedure โดยตรง
 *          (ORM annotation จึงไม่จำเป็น) — ใช้เป็น Plain Domain Model แทน
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long orderId;
    private String customerName;
    private String customerEmail;
    private String orderStatus;
    private BigDecimal totalAmount;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
