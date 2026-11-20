# Shared Conventions

This is the contract between the seven components. Orders, Reviews, Inventory, Delivery and Finance all link to each other through the names below, so the class diagrams, flowcharts and code must use them **exactly as written**.

To change anything here: agree it in the group chat, then open a pull request that updates this file (see [CONTRIBUTING.md](../CONTRIBUTING.md), section 3).

Items marked **PROPOSED** still need the group's agreement. Once agreed, change the marker to **AGREED** and add the date.

---

## 1. Shared ID fields

Every ID is a **`String`**, never a number or a MongoDB `ObjectId`. It is set once when the record is created and **never changes and is never reused**, even after a delete.

| Field name (use exactly) | Owned by | Format | Example | Status |
|---|---|---|---|---|
| `productId` | Products | `PRD-` + 5 digits | `PRD-00007` | **AGREED** (already in the Products code) |
| `customerId` | Customers | `CUS-` + 5 digits | `CUS-00012` | PROPOSED |
| `orderId` | Orders | `ORD-` + 5 digits | `ORD-00103` | PROPOSED |
| `warehouseId` | Inventory / Warehousing | `WHS-` + 3 digits | `WHS-001` | PROPOSED |
| `shipmentId` | Delivery & Logistics | `SHP-` + 5 digits | `SHP-00045` | PROPOSED |
| `reviewId` | Reviews & Ratings | `REV-` + 5 digits | `REV-00230` | PROPOSED |
| `transactionId` | Finance & Reporting | `TXN-` + 5 digits | `TXN-00981` | PROPOSED |

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
* Server: **MongoDB Community Server** (8.0 or newer) installed locally on each machine. **AGREED (final)**: no Docker, no Atlas. Start it with Windows: `net start MongoDB` (or it starts by itself as a service); macOS: `brew services start mongodb-community`.

| Collection | Owner | Holds | Status |
|---|---|---|---|
| `products` | Products | The product catalogue | **AGREED** |
| `counters` | Shared | One document per ID sequence (section 3.2) | **AGREED** |
| `customers` | Customers | Customer accounts and addresses | PROPOSED |
| `orders` | Orders | Orders with their order lines embedded | PROPOSED |
| `shipments` | Delivery & Logistics | Deliveries and tracking status | PROPOSED |
| `warehouses` | Inventory / Warehousing | Warehouse locations | PROPOSED |
| `inventory` | Inventory / Warehousing | Stock per `productId` per `warehouseId` | PROPOSED |
| `reviews` | Reviews & Ratings | Ratings and comments | PROPOSED |
| `transactions` | Finance & Reporting | Payments and refunds | PROPOSED |

Only the owner writes to a collection. Everyone may read any collection.

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

## 5. Open questions (to agree in the group chat)

| # | Question | Proposal | Status |
|---|---|---|---|
| Q1 | Who owns the stock number: `products.stockQuantity` or Inventory's per-warehouse records? | Inventory is the source of truth. It sums `quantity` per `productId` across warehouses and Products shows that total (read-only) | PROPOSED |
| Q2 | Is a SKU (for example `ZNY-2048` in the Inventory mock-up) a separate field? | No separate SKU. `productId` (`PRD-00007`) is the one product identifier shown everywhere | PROPOSED |
| Q3 | Where are products created? | Only in the Products component. Other screens (for example "+ Add product" on the Inventory screen) link to it or are removed | PROPOSED |
| Q4 | Are "Suppliers" in scope? | Check the brief; it is not one of the seven components | OPEN |
| Q5 | How does an order reduce stock without overselling? | One atomic update that succeeds only if enough stock remains: `updateOne({productId, warehouseId, quantity: {$gte: qty}}, {$inc: {quantity: -qty}})` | PROPOSED |

## 6. Change log

| Date | Change | Agreed by |
|---|---|---|
| 2026-11-14 | First version: ID formats, collections, field rules, open questions | Farshad (proposal) |
| 2026-11-20 | Final decision: everyone uses MongoDB Community Server installed locally (no Docker, no Atlas); `docker-compose.yml` removed | Group |
