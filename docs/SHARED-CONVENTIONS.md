# Shared Conventions

This is the contract between the seven components. Orders, Reviews, Inventory, Delivery and Finance all link to each other through the names below, so the class diagrams, flowcharts and code must use them **exactly as written**.

**Everything in this file is final.** It is maintained by the repository owner (Farshad); only the owner changes it (see [CONTRIBUTING.md](../CONTRIBUTING.md), section 3).

---

## 1. Shared ID fields

Every ID is a **`String`**, never a number or a MongoDB `ObjectId`. It is set once when the record is created and **never changes and is never reused**, even after a delete.

| Field name (use exactly) | Owned by | Format | Example | Status |
|---|---|---|---|---|
| `productId` | Products | `PRD-` + 5 digits | `PRD-00007` | **AGREED** (already in the Products code) |
| `customerId` | Customers | `CUS-` + 5 digits | `CUS-00012` | **AGREED** |
| `orderId` | Orders | `ORD-` + 5 digits | `ORD-00103` | **AGREED** |
| `warehouseId` | Inventory / Warehousing | `WHS-` + 3 digits | `WHS-001` | **AGREED** |
| `shipmentId` | Delivery & Logistics | `SHP-` + 5 digits | `SHP-00045` | **AGREED** |
| `reviewId` | Reviews & Ratings | `REV-` + 5 digits | `REV-00230` | **AGREED** |
| `transactionId` | Finance & Reporting | `TXN-` + 5 digits | `TXN-00981` | **AGREED** |

Rules:

* Inside its **own** collection the ID is stored as `_id`. In **another** component's documents it is stored under the field name above. Example: a product is `{ _id: "PRD-00007", ... }`; an order line refers to it as `{ productId: "PRD-00007", ... }`.
* Generate IDs with the shared `counters` collection (section 3.2), not with random numbers.
* In Java, the getter is named after the field: `getProductId()`, `getCustomerId()`, and so on. (Inside the owning class, `Product.getId()` is fine.)

## 2. Field naming

| Rule | Example |
|---|---|
| camelCase for every field name, in Java and in MongoDB | `unitPrice`, `createdAt`, `stockQuantity` |
| Money is `BigDecimal` in Java and `Decimal128` in MongoDB. **Never `double`.** Currency is GBP (£) | `price`, `unitPrice`, `totalAmount` |
| Dates/times are `java.time.Instant` in Java and BSON `Date` in MongoDB (UTC) | `createdAt`, `updatedAt`, `orderedAt` |
| Status values are UPPERCASE strings (a Java `enum`) | `PENDING`, `SHIPPED`, `DELIVERED` |
| Booleans start with `is`/`has` in Java getters, plain in MongoDB | `isActive()` / `active: true` |
| Quantities are whole numbers (`int`), never negative | `quantity`, `stockQuantity` |

## 3. MongoDB

### 3.1 One database, one set of collections each

* Database name: **`marketplace`**
* Connection: `mongodb://localhost:27017` by default. Override with the environment variable **`MONGODB_URI`**, and the database with **`MONGODB_DATABASE`**. Never hard-code a password.
* Server: **MongoDB Community Server** (8.0 or newer) installed locally on each machine. **AGREED**: no Docker, no Atlas. Start it with Windows: `net start MongoDB` (or it starts by itself as a service); macOS: `brew services start mongodb-community`.

| Collection | Owner | Holds | Status |
|---|---|---|---|
| `products` | Products | The product catalogue | **AGREED** |
| `counters` | Shared | One document per ID sequence (section 3.2) | **AGREED** |
| `customers` | Customers | Customer accounts and addresses | **AGREED** |
| `orders` | Orders | Orders with their order lines embedded | **AGREED** |
| `shipments` | Delivery & Logistics | Deliveries and tracking status | **AGREED** |
| `warehouses` | Inventory / Warehousing | Warehouse locations | **AGREED** |
| `inventory` | Inventory / Warehousing | Stock per `productId` per `warehouseId` | **AGREED** |
| `reviews` | Reviews & Ratings | Ratings and comments | **AGREED** |
| `transactions` | Finance & Reporting | Payments and refunds | **AGREED** |

Only the owner writes to a collection. Everyone may read any collection. The one exception: Orders reduces `products.stockQuantity` with the single atomic update in Q5.

### 3.2 The shared `counters` collection

Each component owns **one document** in `counters`, keyed by its collection name:

```json
{ "_id": "products",  "seq": 15 }
{ "_id": "customers", "seq": 4 }
{ "_id": "orders",    "seq": 22 }
```

Get the next number with one atomic update, so two users can never get the same ID:

```java
Document doc = counters.findOneAndUpdate(
        Filters.eq("_id", "orders"),
        Updates.inc("seq", 1),
        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
String orderId = String.format("ORD-%05d", doc.getInteger("seq"));
```

(The Products component already does this in `MongoProductRepository.nextProductId()`. Copy the idea, not the class.)

## 4. Links between components

| From | Stores | Points to |
|---|---|---|
| Orders: each order | `customerId` | Customers |
| Orders: each order line | `productId`, plus a **snapshot** of `productName` and `unitPrice` at the time of purchase | Products |
| Delivery: each shipment | `orderId`, `warehouseId` | Orders, Inventory |
| Inventory: each stock record | `productId`, `warehouseId`, `quantity`, `minLevel` | Products |
| Reviews: each review | `productId`, `customerId`, optional `orderId` | Products, Customers, Orders |
| Finance: each transaction | `orderId`, `customerId` | Orders, Customers |

Why the snapshot? If a product's price changes later, old orders must still show what the customer actually paid.

Before creating a record that points to a product, check that the product exists and `active` is `true`.

## 5. Decisions

| # | Question | Decision | Status |
|---|---|---|---|
| Q1 | Who owns the stock number: `products.stockQuantity` or Inventory's per-warehouse records? | **Products owns it.** `products.stockQuantity` is the stock available for sale; it is set and edited in the Products screen. Inventory's per-warehouse records only show where that stock is kept | **AGREED** |
| Q2 | Is a SKU (for example `ZNY-2048` in the Inventory mock-up) a separate field? | No separate SKU. `productId` (`PRD-00007`) is the one product identifier shown everywhere | **AGREED** |
| Q3 | Where are products created? | Only in the Products component. Other screens (for example "+ Add product" on the Inventory screen) link to it or are removed | **AGREED** |
| Q4 | Are "Suppliers" in scope? | No. Suppliers is not one of the seven components, so no component builds it | **AGREED** |
| Q5 | How does an order reduce stock without overselling? | One atomic update on the product that succeeds only if enough stock remains: `updateOne({_id: productId, stockQuantity: {$gte: qty}}, {$inc: {stockQuantity: -qty}})`; `matchedCount == 0` means "not enough stock". This is the only write another component may make to `products` | **AGREED** |

## 6. Change log

| Date | Change | Agreed by |
|---|---|---|
| 2026-11-14 | First version: ID formats, collections, field rules, open questions | Farshad |
| 2026-11-20 | Final decision: everyone uses MongoDB Community Server installed locally (no Docker, no Atlas); `docker-compose.yml` removed | Farshad |
| 2026-11-20 | All IDs, collections and decisions (Q1 to Q5) are final: nothing is proposed any more | Farshad |
