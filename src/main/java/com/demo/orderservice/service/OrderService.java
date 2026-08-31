package com.demo.orderservice.service;

import com.demo.orderservice.dto.CreateOrderRequestDto;
import com.demo.orderservice.dto.OrderResponseDto;
import com.demo.orderservice.dto.PagedResponseDto;

public interface OrderService {

    OrderResponseDto createOrder(CreateOrderRequestDto request);

    OrderResponseDto getOrderById(Long orderId);

    PagedResponseDto<OrderResponseDto> getOrders(String status, int pageNumber, int pageSize);

    OrderResponseDto updateOrderStatus(Long orderId, String newStatus);
}
