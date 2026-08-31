package com.demo.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponseDto {
    private Long orderItemId;
    private String productCode;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
