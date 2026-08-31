/* =====================================================================
   DEMO: Order Management System - Schema + Stored Procedures
   Database: Microsoft SQL Server
   ===================================================================== */

IF DB_ID('OrderDemoDB') IS NULL
BEGIN
    CREATE DATABASE OrderDemoDB;
END
GO

USE OrderDemoDB;
GO

/* ---------------------------------------------------------------------
   1. TABLES
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.Orders', 'U') IS NOT NULL DROP TABLE dbo.OrderItems;
IF OBJECT_ID('dbo.OrderItems', 'U') IS NOT NULL DROP TABLE dbo.OrderItems;
IF OBJECT_ID('dbo.Orders', 'U') IS NOT NULL DROP TABLE dbo.Orders;
GO

CREATE TABLE dbo.Orders (
    OrderId         BIGINT IDENTITY(1,1) PRIMARY KEY,
    CustomerName    NVARCHAR(200)   NOT NULL,
    CustomerEmail   NVARCHAR(200)   NULL,
    OrderStatus     VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING, CONFIRMED, CANCELLED
    TotalAmount     DECIMAL(18,2)   NOT NULL DEFAULT 0,
    CreatedDate     DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedDate     DATETIME2       NULL
);
GO

CREATE TABLE dbo.OrderItems (
    OrderItemId     BIGINT IDENTITY(1,1) PRIMARY KEY,
    OrderId         BIGINT          NOT NULL FOREIGN KEY REFERENCES dbo.Orders(OrderId),
    ProductCode     NVARCHAR(50)    NOT NULL,
    ProductName     NVARCHAR(200)   NOT NULL,
    Quantity        INT             NOT NULL,
    UnitPrice       DECIMAL(18,2)   NOT NULL,
    LineTotal       AS (Quantity * UnitPrice) PERSISTED
);
GO

/* ---------------------------------------------------------------------
   2. TYPE: Table-Valued Parameter สำหรับส่งรายการสินค้า (OrderItems) เข้า SP
   --------------------------------------------------------------------- */
IF TYPE_ID('dbo.OrderItemTableType') IS NOT NULL
    DROP TYPE dbo.OrderItemTableType;
GO

CREATE TYPE dbo.OrderItemTableType AS TABLE (
    ProductCode     NVARCHAR(50),
    ProductName     NVARCHAR(200),
    Quantity        INT,
    UnitPrice       DECIMAL(18,2)
);
GO

/* ---------------------------------------------------------------------
   3. SP: sp_CreateOrder
   - รับ Input parameters + Table-Valued Parameter (รายการสินค้า)
   - คืนค่า OrderId ผ่าน OUTPUT parameter
   - คืนค่า Return Code ผ่าน RETURN (0 = success, <0 = error)
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dbo.sp_CreateOrder
    @CustomerName   NVARCHAR(200),
    @CustomerEmail  NVARCHAR(200) = NULL,
    @Items          dbo.OrderItemTableType READONLY,
    @NewOrderId     BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM @Items)
        BEGIN
            RAISERROR('Order must contain at least one item.', 16, 1);
            RETURN -1;
        END

        BEGIN TRANSACTION;

        INSERT INTO dbo.Orders (CustomerName, CustomerEmail, OrderStatus, TotalAmount, CreatedDate)
        VALUES (@CustomerName, @CustomerEmail, 'PENDING', 0, SYSUTCDATETIME());

        SET @NewOrderId = SCOPE_IDENTITY();

        INSERT INTO dbo.OrderItems (OrderId, ProductCode, ProductName, Quantity, UnitPrice)
        SELECT @NewOrderId, ProductCode, ProductName, Quantity, UnitPrice
        FROM @Items;

        UPDATE dbo.Orders
        SET TotalAmount = (SELECT SUM(LineTotal) FROM dbo.OrderItems WHERE OrderId = @NewOrderId)
        WHERE OrderId = @NewOrderId;

        COMMIT TRANSACTION;
        RETURN 0;  -- Success
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0
            ROLLBACK TRANSACTION;

        DECLARE @ErrMsg NVARCHAR(4000) = ERROR_MESSAGE();
        DECLARE @ErrSeverity INT = ERROR_SEVERITY();
        RAISERROR(@ErrMsg, @ErrSeverity, 1);
        RETURN -99; -- Unexpected error
    END CATCH
END
GO

/* ---------------------------------------------------------------------
   4. SP: sp_GetOrderById
   - คืนค่า Order header (Result Set 1) + Order Items (Result Set 2)
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dbo.sp_GetOrderById
    @OrderId BIGINT
AS
BEGIN
    SET NOCOUNT ON;

    -- Result Set 1: Order Header
    SELECT
        OrderId, CustomerName, CustomerEmail, OrderStatus,
        TotalAmount, CreatedDate, UpdatedDate
    FROM dbo.Orders
    WHERE OrderId = @OrderId;

    -- Result Set 2: Order Items
    SELECT
        OrderItemId, OrderId, ProductCode, ProductName,
        Quantity, UnitPrice, LineTotal
    FROM dbo.OrderItems
    WHERE OrderId = @OrderId;
END
GO

/* ---------------------------------------------------------------------
   5. SP: sp_GetOrderList  (รองรับ Paging + Filter ตามสถานะ)
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dbo.sp_GetOrderList
    @Status         VARCHAR(20) = NULL,
    @PageNumber     INT = 1,
    @PageSize       INT = 10,
    @TotalCount     INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT @TotalCount = COUNT(*)
    FROM dbo.Orders
    WHERE (@Status IS NULL OR OrderStatus = @Status);

    SELECT
        OrderId, CustomerName, CustomerEmail, OrderStatus,
        TotalAmount, CreatedDate, UpdatedDate
    FROM dbo.Orders
    WHERE (@Status IS NULL OR OrderStatus = @Status)
    ORDER BY CreatedDate DESC
    OFFSET (@PageNumber - 1) * @PageSize ROWS
    FETCH NEXT @PageSize ROWS ONLY;
END
GO

/* ---------------------------------------------------------------------
   6. SP: sp_UpdateOrderStatus
   --------------------------------------------------------------------- */
CREATE OR ALTER PROCEDURE dbo.sp_UpdateOrderStatus
    @OrderId        BIGINT,
    @NewStatus      VARCHAR(20),
    @RowsAffected   INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.Orders
    SET OrderStatus = @NewStatus,
        UpdatedDate = SYSUTCDATETIME()
    WHERE OrderId = @OrderId;

    SET @RowsAffected = @@ROWCOUNT;
END
GO

PRINT 'Schema and Stored Procedures created successfully.';
