package com.demo.orderservice.repository.impl;

import com.demo.orderservice.entity.Order;
import com.demo.orderservice.entity.OrderItem;
import com.demo.orderservice.exception.DataAccessOperationException;
import com.demo.orderservice.repository.OrderRepository;
import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository Layer Implementation
 * -------------------------------------------------------------
 * จุดสำคัญ: ทุก method เรียกใช้งาน Stored Procedure โดยตรงผ่าน JdbcTemplate.execute(...)
 * ไม่มีการใช้ JPA/Hibernate หรือ query แบบ ORM ใดๆ ทั้งสิ้น
 *
 * เทคนิคที่ใช้:
 *  - CallableStatement            : เรียก {call dbo.sp_xxx(...)}
 *  - registerOutParameter         : รับค่า OUTPUT parameter จาก SP
 *  - SQLServerDataTable           : ส่ง Table-Valued Parameter (TVP) เข้า SP (รายการสินค้า)
 *  - getMoreResults()             : อ่าน multiple result sets จาก SP เดียว (header + items)
 * -------------------------------------------------------------
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String SP_CREATE_ORDER        = "{call dbo.sp_CreateOrder(?, ?, ?, ?)}";
    private static final String SP_GET_ORDER_BY_ID      = "{call dbo.sp_GetOrderById(?)}";
    private static final String SP_GET_ORDER_LIST       = "{call dbo.sp_GetOrderList(?, ?, ?, ?)}";
    private static final String SP_UPDATE_ORDER_STATUS  = "{call dbo.sp_UpdateOrderStatus(?, ?, ?)}";

    private static final String TVP_TYPE_NAME = "dbo.OrderItemTableType";

    /* =====================================================================
       1) CREATE ORDER  -> sp_CreateOrder
       ===================================================================== */
    @Override
    public Long createOrder(Order order) {
        try {
            return jdbcTemplate.execute(SP_CREATE_ORDER, (CallableStatement cs) -> {
                cs.setString(1, order.getCustomerName());
                cs.setString(2, order.getCustomerEmail());

                // ----- สร้าง Table-Valued Parameter (TVP) จากรายการสินค้า -----
                SQLServerDataTable itemsTable = new SQLServerDataTable();
                itemsTable.addColumnMetadata("ProductCode", Types.NVARCHAR);
                itemsTable.addColumnMetadata("ProductName", Types.NVARCHAR);
                itemsTable.addColumnMetadata("Quantity", Types.INTEGER);
                itemsTable.addColumnMetadata("UnitPrice", Types.DECIMAL);

                for (OrderItem item : order.getItems()) {
                    itemsTable.addRow(
                            item.getProductCode(),
                            item.getProductName(),
                            item.getQuantity(),
                            item.getUnitPrice()
                    );
                }

                // ต้อง unwrap เป็น SQLServerCallableStatement เพื่อใช้ setStructured (เฉพาะ MS SQL Server)
                cs.unwrap(SQLServerCallableStatement.class)
                        .setStructured(3, TVP_TYPE_NAME, itemsTable);

                // OUTPUT parameter: NewOrderId
                cs.registerOutParameter(4, Types.BIGINT);

                cs.execute();

                long newOrderId = cs.getLong(4);
                log.info("sp_CreateOrder executed successfully. NewOrderId={}", newOrderId);
                return newOrderId;
            });
        } catch (Exception ex) {
            log.error("Failed to execute sp_CreateOrder for customer [{}]", order.getCustomerName(), ex);
            throw new DataAccessOperationException("Failed to create order via sp_CreateOrder", ex);
        }
    }

    /* =====================================================================
       2) GET ORDER BY ID -> sp_GetOrderById (2 result sets)
       ===================================================================== */
    @Override
    public Optional<Order> findById(Long orderId) {
        try {
            Order order = jdbcTemplate.execute(SP_GET_ORDER_BY_ID, (CallableStatement cs) -> {
                cs.setLong(1, orderId);

                boolean hasResultSet = cs.execute();
                Order result = null;

                // ----- Result Set 1: Order Header -----
                if (hasResultSet) {
                    try (ResultSet rs = cs.getResultSet()) {
                        if (rs.next()) {
                            result = mapOrderHeader(rs);
                        }
                    }
                }

                if (result == null) {
                    return null; // ไม่พบ Order -> ปล่อยให้ Service Layer ตัดสินใจ throw NotFound
                }

                // ----- Result Set 2: Order Items -----
                boolean hasMore = cs.getMoreResults();
                if (hasMore) {
                    try (ResultSet rs = cs.getResultSet()) {
                        List<OrderItem> items = new ArrayList<>();
                        while (rs.next()) {
                            items.add(mapOrderItem(rs));
                        }
                        result.setItems(items);
                    }
                }

                return result;
            });

            return Optional.ofNullable(order);
        } catch (Exception ex) {
            log.error("Failed to execute sp_GetOrderById for orderId [{}]", orderId, ex);
            throw new DataAccessOperationException("Failed to fetch order via sp_GetOrderById", ex);
        }
    }

    /* =====================================================================
       3) GET ORDER LIST -> sp_GetOrderList (paging + total count OUTPUT)
       ===================================================================== */
    @Override
    public OrderPage findAll(String status, int pageNumber, int pageSize) {
        try {
            return jdbcTemplate.execute(SP_GET_ORDER_LIST, (CallableStatement cs) -> {
                if (status == null) {
                    cs.setNull(1, Types.VARCHAR);
                } else {
                    cs.setString(1, status);
                }
                cs.setInt(2, pageNumber);
                cs.setInt(3, pageSize);
                cs.registerOutParameter(4, Types.INTEGER); // TotalCount OUTPUT

                boolean hasResultSet = cs.execute();
                List<Order> orders = new ArrayList<>();

                if (hasResultSet) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            orders.add(mapOrderHeader(rs));
                        }
                    }
                }

                int totalCount = cs.getInt(4);
                return new OrderPage(orders, totalCount);
            });
        } catch (Exception ex) {
            log.error("Failed to execute sp_GetOrderList (status={}, page={}, size={})",
                    status, pageNumber, pageSize, ex);
            throw new DataAccessOperationException("Failed to fetch order list via sp_GetOrderList", ex);
        }
    }

    /* =====================================================================
       4) UPDATE ORDER STATUS -> sp_UpdateOrderStatus
       ===================================================================== */
    @Override
    public int updateStatus(Long orderId, String newStatus) {
        try {
            return jdbcTemplate.execute(SP_UPDATE_ORDER_STATUS, (CallableStatement cs) -> {
                cs.setLong(1, orderId);
                cs.setString(2, newStatus);
                cs.registerOutParameter(3, Types.INTEGER); // RowsAffected OUTPUT

                cs.execute();
                return cs.getInt(3);
            });
        } catch (Exception ex) {
            log.error("Failed to execute sp_UpdateOrderStatus for orderId [{}]", orderId, ex);
            throw new DataAccessOperationException("Failed to update order status via sp_UpdateOrderStatus", ex);
        }
    }

    /* =====================================================================
       Mapping Helpers: ResultSet (Stored Procedure) -> Entity
       ===================================================================== */
    private Order mapOrderHeader(ResultSet rs) throws SQLException {
        return Order.builder()
                .orderId(rs.getLong("OrderId"))
                .customerName(rs.getString("CustomerName"))
                .customerEmail(rs.getString("CustomerEmail"))
                .orderStatus(rs.getString("OrderStatus"))
                .totalAmount(rs.getBigDecimal("TotalAmount"))
                .createdDate(rs.getTimestamp("CreatedDate") != null
                        ? rs.getTimestamp("CreatedDate").toLocalDateTime() : null)
                .updatedDate(rs.getTimestamp("UpdatedDate") != null
                        ? rs.getTimestamp("UpdatedDate").toLocalDateTime() : null)
                .items(new ArrayList<>())
                .build();
    }

    private OrderItem mapOrderItem(ResultSet rs) throws SQLException {
        return OrderItem.builder()
                .orderItemId(rs.getLong("OrderItemId"))
                .orderId(rs.getLong("OrderId"))
                .productCode(rs.getString("ProductCode"))
                .productName(rs.getString("ProductName"))
                .quantity(rs.getInt("Quantity"))
                .unitPrice(rs.getBigDecimal("UnitPrice"))
                .lineTotal(rs.getBigDecimal("LineTotal"))
                .build();
    }
}
