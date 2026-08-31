package com.demo.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequestDto {

    @NotBlank(message = "productCode is required")
    private String productCode;

    @NotBlank(message = "productName is required")
    private String productName;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "unitPrice is required")
    @Min(value = 0, message = "unitPrice must not be negative")
    private BigDecimal unitPrice;
}
