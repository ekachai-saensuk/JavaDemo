package com.demo.orderservice.service.impl;

import com.demo.orderservice.dto.*;
import com.demo.orderservice.entity.Order;
import com.demo.orderservice.entity.OrderItem;
import com.demo.orderservice.exception.BusinessException;
import com.demo.orderservice.exception.ResourceNotFoundException;
import com.demo.orderservice.repository.OrderRepository;
import com.demo.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service Layer
 * - ตรวจสอบกฎทางธุรกิจ (Business Validation) ก่อนเรียก Repository
 * - แปลงข้อมูลระหว่าง DTO (จาก Controller) <-> Entity (สำหรับ Repository)
 * - ไม่ยุ่งเกี่ยวกับรายละเอียดของ SQL / Stored Procedure โดยตรง (แยกความรับผิดชอบชัดเจน)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    private static final List<String> VALID_STATUSES = List.of("PENDING", "CONFIRMED", "CANCELLED");

    @Override
    public OrderResponseDto createOrder(CreateOrderRequestDto request) {

        // ----- Business Validation เพิ่มเติมนอกเหนือจาก Bean Validation ใน DTO -----
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("An order must contain at least one item", "ORDER_EMPTY_ITEMS");
        }

        BigDecimal estimatedTotal = request.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (estimatedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Order total amount must be greater than zero", "ORDER_INVALID_TOTAL");
        }

        // ----- Map DTO -> Entity -----
        Order order = Order.builder()
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .items(request.getItems().stream()
                        .map(i -> OrderItem.builder()
                                .productCode(i.getProductCode())
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        Long newOrderId = orderRepository.createOrder(order);
        log.info("Order created successfully with id={}", newOrderId);

        // ดึงข้อมูลเต็มกลับมาอีกครั้งเพื่อคืนค่าที่ Database คำนวณให้ (เช่น TotalAmount, CreatedDate)
        return getOrderById(newOrderId);
    }

    @Override
    public OrderResponseDto getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        return mapToResponseDto(order);
    }

    @Override
    public PagedResponseDto<OrderResponseDto> getOrders(String status, int pageNumber, int pageSize) {

        if (pageNumber < 1) {
            throw new BusinessException("pageNumber must be >= 1", "INVALID_PAGE_NUMBER");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException("pageSize must be between 1 and 100", "INVALID_PAGE_SIZE");
        }
        if (status != null && !VALID_STATUSES.contains(status.toUpperCase())) {
            throw new BusinessException("status must be one of " + VALID_STATUSES, "INVALID_STATUS_FILTER");
        }

        OrderRepository.OrderPage page = orderRepository.findAll(status, pageNumber, pageSize);

        List<OrderResponseDto> content = page.content().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) page.totalCount() / pageSize);

        return PagedResponseDto.<OrderResponseDto>builder()
                .content(content)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalCount(page.totalCount())
                .totalPages(totalPages)
                .build();
    }

    @Override
    public OrderResponseDto updateOrderStatus(Long orderId, String newStatus) {

        if (!VALID_STATUSES.contains(newStatus.toUpperCase())) {
            throw new BusinessException("Invalid order status: " + newStatus, "INVALID_STATUS");
        }

        int rowsAffected = orderRepository.updateStatus(orderId, newStatus.toUpperCase());

        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }

        log.info("Order [{}] status updated to [{}]", orderId, newStatus);
        return getOrderById(orderId);
    }

    /* ---------------------------------------------------------------------
       Mapping: Entity -> Response DTO
       --------------------------------------------------------------------- */
    private OrderResponseDto mapToResponseDto(Order order) {
        List<OrderItemResponseDto> itemDtos = order.getItems().stream()
                .map(item -> OrderItemResponseDto.builder()
                        .orderItemId(item.getOrderItemId())
                        .productCode(item.getProductCode())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponseDto.builder()
                .orderId(order.getOrderId())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .orderStatus(order.getOrderStatus())
                .totalAmount(order.getTotalAmount())
                .createdDate(order.getCreatedDate())
                .updatedDate(order.getUpdatedDate())
                .items(itemDtos)
                .build();
    }
}
