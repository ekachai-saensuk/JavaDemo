package com.demo.orderservice.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequestDto {

    @Pattern(regexp = "PENDING|CONFIRMED|CANCELLED", message = "status must be one of: PENDING, CONFIRMED, CANCELLED")
    private String status;
}
