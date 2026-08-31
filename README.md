# Order Management System (Demo)

ระบบสั่งซื้อสินค้าตัวอย่างแบบ **Production-ready**, สถาปัตยกรรมแบบ **Layered / Clean Architecture**
ใช้ **Java Spring Boot** + **Microsoft SQL Server** โดยเรียกข้อมูลผ่าน **Stored Procedure ล้วน** (ไม่ใช้ ORM query เช่น JPQL/Hibernate)

---

## 1. โครงสร้างโฟลเดอร์

```
order-service/
├── pom.xml
├── docker-compose.yml                     # รัน SQL Server ผ่าน Docker (สำหรับ demo)
├── src/main/resources/
│   ├── application.yml                    # การตั้งค่า DB connection
│   └── db/01_schema_and_procedures.sql    # DDL ตาราง + Stored Procedures ทั้งหมด
└── src/main/java/com/demo/orderservice/
    ├── OrderServiceApplication.java
    ├── entity/                            # Domain Model (Entity Layer)
    │   ├── Order.java
    │   └── OrderItem.java
    ├── dto/                                # Data Transfer Object (Controller <-> Client)
    │   ├── CreateOrderRequestDto.java
    │   ├── OrderItemRequestDto.java
    │   ├── OrderResponseDto.java
    │   ├── OrderItemResponseDto.java
    │   ├── UpdateOrderStatusRequestDto.java
    │   ├── PagedResponseDto.java
    │   └── ApiResponse.java
    ├── repository/                        # Repository Layer (Execute Stored Procedure)
    │   ├── OrderRepository.java            (interface)
    │   └── impl/OrderRepositoryImpl.java   (JdbcTemplate + CallableStatement)
    ├── service/                           # Service Layer (Business Logic)
    │   ├── OrderService.java               (interface)
    │   └── impl/OrderServiceImpl.java
    ├── controller/                        # Controller Layer (REST API)
    │   └── OrderController.java
    └── exception/                         # Exception Handling ทุก Layer
        ├── BusinessException.java
        ├── ResourceNotFoundException.java
        ├── DataAccessOperationException.java
        ├── ErrorResponse.java
        └── GlobalExceptionHandler.java
```

---

## 2. Data Flow ของสถาปัตยกรรม

```
HTTP Request
   │
   ▼
[Controller Layer]  ──►  รับ/validate DTO, ไม่มี business logic
   │
   ▼
[Service Layer]     ──►  ตรวจกฎธุรกิจ (business rule), แปลง DTO -> Entity
   │
   ▼
[Repository Layer]  ──►  Execute Stored Procedure ผ่าน CallableStatement
   │
   ▼
[SQL Server]         ──►  sp_CreateOrder / sp_GetOrderById / sp_GetOrderList / sp_UpdateOrderStatus
```

---

## 3. วิธีรัน Demo

### 3.1 เตรียม Database
```bash
docker compose up -d
# รอ ~15 วินาทีให้ SQL Server พร้อม แล้ว execute script ต่อไปนี้ด้วย Azure Data Studio / sqlcmd:
# src/main/resources/db/01_schema_and_procedures.sql
```

หรือใช้ `sqlcmd` โดยตรง:
```bash
sqlcmd -S localhost -U sa -P 'YourStrong@Passw0rd' -i src/main/resources/db/01_schema_and_procedures.sql
```

### 3.2 รันแอปพลิเคชัน
```bash
mvn spring-boot:run
```

แอปจะรันที่ `http://localhost:8080` และมี Swagger UI ที่ `http://localhost:8080/swagger-ui.html`

### 3.3 ทดสอบ API

**สร้าง Order:**
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Somchai Jaidee",
    "customerEmail": "somchai@example.com",
    "items": [
      { "productCode": "SKU-001", "productName": "Wireless Mouse", "quantity": 2, "unitPrice": 15.00 },
      { "productCode": "SKU-002", "productName": "Mechanical Keyboard", "quantity": 1, "unitPrice": 55.00 }
    ]
  }'
```

**ดึงข้อมูล Order:**
```bash
curl http://localhost:8080/api/v1/orders/1
```

**ดึงรายการ Order (paging + filter):**
```bash
curl "http://localhost:8080/api/v1/orders?status=PENDING&pageNumber=1&pageSize=10"
```

**อัปเดตสถานะ:**
```bash
curl -X PATCH http://localhost:8080/api/v1/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "CONFIRMED" }'
```

---

## 4. การ Map ค่าจาก Stored Procedure เข้าสู่ Entity/DTO (สรุป)

1. **Input (Java → Stored Procedure)**
   - Parameter ปกติ (`IN`) ใช้ `CallableStatement.setXxx(index, value)` ตามตำแหน่งของ `?` ใน `{call sp_xxx(?,?,?)}`
   - รายการสินค้าหลายแถว (array-like) ถูกส่งเป็น **Table-Valued Parameter (TVP)** ผ่าน `SQLServerDataTable` แล้ว unwrap เป็น `SQLServerCallableStatement.setStructured(...)` — เป็นฟีเจอร์เฉพาะของ SQL Server JDBC Driver ที่ทำให้ส่งข้อมูลเป็น "ตาราง" เข้า SP ได้ในครั้งเดียว แทนการ insert ทีละแถวด้วย loop
   - Parameter แบบ `OUTPUT` ต้อง `registerOutParameter(index, java.sql.Types.X)` ก่อน `execute()` แล้วค่อยอ่านค่าออกด้วย `cs.getXxx(index)` **หลัง** execute เสร็จ

2. **Output (Stored Procedure → Java Entity)**
   - ผลลัพธ์แบบ `SELECT` จะได้กลับมาเป็น `ResultSet` ผ่าน `cs.getResultSet()`
   - หาก SP คืนหลาย Result Set (เช่น `sp_GetOrderById` คืน Order header และ Order items แยกกัน) ต้องวนอ่านด้วย `cs.getMoreResults()` เพื่อขยับไป Result Set ถัดไป
   - แต่ละแถวใน `ResultSet` จะถูก map เป็น field ของ Entity ทีละคอลัมน์ (`rs.getLong("OrderId")`, `rs.getString("CustomerName")` ฯลฯ) ใน method `mapOrderHeader()` / `mapOrderItem()` ของ `OrderRepositoryImpl`
   - Entity (`Order`, `OrderItem`) ที่ได้จาก Repository Layer จะถูกแปลง (map) ต่อเป็น DTO (`OrderResponseDto`, `OrderItemResponseDto`) ใน Service Layer เพื่อควบคุมว่า field ใดควรเปิดเผยให้ client เห็นบ้าง (แยก concern ระหว่างโครงสร้าง DB กับ contract ของ API)

---

## 5. Exception Handling ในแต่ละ Layer

| Layer | ทำหน้าที่ | Exception ที่เกี่ยวข้อง |
|---|---|---|
| **Repository** | ครอบ (wrap) exception จาก JDBC/SQL Server (เช่น `SQLException`, timeout, constraint violation) ไม่ให้หลุดขึ้นไปเป็น raw exception | `DataAccessOperationException` |
| **Service** | ตรวจกฎธุรกิจ เช่น order ต้องมีอย่างน้อย 1 รายการ, total ต้อง > 0, status ต้องอยู่ใน enum ที่กำหนด, ค้นหาไม่พบ | `BusinessException`, `ResourceNotFoundException` |
| **Controller** | รับ error จาก Bean Validation (`@Valid`) ที่ตัว DTO เอง เช่น field required, format ผิด | `MethodArgumentNotValidException` (Spring built-in) |
| **Global** | จุดรวมจัดการทุก exception ที่เหลือ แปลงเป็น HTTP status + JSON response รูปแบบเดียวกันทั้งระบบ | `GlobalExceptionHandler` (`@RestControllerAdvice`) |

**หลักการสำคัญ**: รายละเอียดทางเทคนิคของ Database (เช่น SQL error message ดิบ, stack trace) จะไม่ถูกส่งออกไปให้ client โดยตรง — `GlobalExceptionHandler` จะแปลงเป็นข้อความ error ที่ปลอดภัยเสมอ พร้อม log รายละเอียดเต็มไว้ฝั่ง server (`log.error(...)`) เพื่อการ debug

ตัวอย่าง Error Response:
```json
{
  "success": false,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Order not found with id: 999",
  "path": "/api/v1/orders/999",
  "timestamp": "2026-08-31T10:15:30"
}
```

---

## 6. Stored Procedures ที่ใช้ในระบบ

| Stored Procedure | หน้าที่ | Parameters |
|---|---|---|
| `sp_CreateOrder` | สร้าง Order + Order Items ใน transaction เดียว | IN: CustomerName, CustomerEmail, Items (TVP) / OUT: NewOrderId |
| `sp_GetOrderById` | ดึง Order header + items (2 result sets) | IN: OrderId |
| `sp_GetOrderList` | ดึงรายการ Order พร้อม paging/filter | IN: Status, PageNumber, PageSize / OUT: TotalCount |
| `sp_UpdateOrderStatus` | เปลี่ยนสถานะ Order | IN: OrderId, NewStatus / OUT: RowsAffected |

รายละเอียด SQL เต็มอยู่ที่ `src/main/resources/db/01_schema_and_procedures.sql`

---

## 7. หมายเหตุสำหรับนำไปต่อยอดจริง (Production Checklist)

- เพิ่ม connection pool tuning (`hikari`) ให้เหมาะกับ load จริง
- เพิ่ม Idempotency key สำหรับ `POST /orders` ป้องกันการสร้างซ้ำจาก retry
- เพิ่ม Authentication/Authorization (เช่น JWT) ที่ Controller Layer
- เพิ่ม Distributed Tracing / Correlation ID สำหรับ log
- ย้าย credential ใน `application.yml` ไปเก็บใน secret manager (เช่น Vault, AWS Secrets Manager) แทนการ hardcode
