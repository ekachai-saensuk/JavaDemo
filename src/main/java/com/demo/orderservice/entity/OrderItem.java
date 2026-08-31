package com.demo.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Domain Entity: แทนโครงสร้างข้อมูล 1 แถวในตาราง dbo.OrderItems
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private Long orderItemId;
    private Long orderId;
    private String productCode;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
