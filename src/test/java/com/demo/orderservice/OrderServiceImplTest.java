package com.demo.orderservice;

import com.demo.orderservice.dto.CreateOrderRequestDto;
import com.demo.orderservice.dto.OrderItemRequestDto;
import com.demo.orderservice.entity.Order;
import com.demo.orderservice.exception.BusinessException;
import com.demo.orderservice.exception.ResourceNotFoundException;
import com.demo.orderservice.repository.OrderRepository;
import com.demo.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository);
    }

    @Test
    void createOrder_shouldSucceed_whenRequestIsValid() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setCustomerName("Somchai Jaidee");
        request.setCustomerEmail("somchai@example.com");
        request.setItems(List.of(new OrderItemRequestDto("SKU-001", "Wireless Mouse", 2, BigDecimal.valueOf(15.00))));

        Order savedOrder = Order.builder()
                .orderId(100L)
                .customerName("Somchai Jaidee")
                .customerEmail("somchai@example.com")
                .orderStatus("PENDING")
                .totalAmount(BigDecimal.valueOf(30.00))
                .createdDate(LocalDateTime.now())
                .items(List.of())
                .build();

        when(orderRepository.createOrder(any(Order.class))).thenReturn(100L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(savedOrder));

        var result = orderService.createOrder(request);

        assertThat(result.getOrderId()).isEqualTo(100L);
        assertThat(result.getOrderStatus()).isEqualTo("PENDING");
    }

    @Test
    void createOrder_shouldThrowBusinessException_whenItemsEmpty() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setCustomerName("Somchai Jaidee");
        request.setItems(List.of());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getOrderById_shouldThrowNotFound_whenOrderDoesNotExist() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
