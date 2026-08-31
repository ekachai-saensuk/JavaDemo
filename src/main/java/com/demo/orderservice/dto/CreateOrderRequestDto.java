package com.demo.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO สำหรับ Endpoint: POST /api/v1/orders
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequestDto {

    @NotBlank(message = "customerName is required")
    private String customerName;

    @Email(message = "customerEmail must be a valid email address")
    private String customerEmail;

    @NotEmpty(message = "items must contain at least one product")
    @Valid
    private List<OrderItemRequestDto> items;
}
