# Shared Conventions (FINAL, version 1.0)

This is the contract between the seven components. They all use **one MongoDB database**, so every component must store and read data **exactly as written here**: the same collection names, field names, ID formats, types and status values. Class diagrams, flowcharts, code and seed data must all match it.

**Status: FINAL.** Everything in this file is agreed (20 November 2026). Do not invent your own names or formats. If you really need a change (for example a new field), ask in the group chat first, then open a pull request that updates this file and add a line to the change log (section 9). Adding a new **optional** field to your own collection is fine; renaming or removing a field others read is not, unless the group agrees.

Contents:

1. [Shared ID fields](#1-shared-id-fields)
2. [Field naming and types](#2-field-naming-and-types)
3. [MongoDB: database, connection, ownership](#3-mongodb)
4. [Collection schemas](#4-collection-schemas) (one table per collection)
5. [Links between components](#5-links-between-components)
6. [Status values and how they move](#6-status-values-and-how-they-move)
7. [Decisions on the earlier open questions](#7-decisions)
8. [Seed data](#8-seed-data)
9. [Change log](#9-change-log)

---

## 1. Shared ID fields

Every ID is a **`String`**, never a number or a MongoDB `ObjectId`. It is set once when the record is created, **never changes and is never reused**, even after a delete.

| Field name (use exactly) | Owned by | Collection | Format | Example |
|---|---|---|---|---|
| `productId` | Products | `products` | `PRD-` + 5 digits | `PRD-00007` |
| `customerId` | Customers | `customers` | `CUS-` + 5 digits | `CUS-00012` |
| `orderId` | Orders | `orders` | `ORD-` + 5 digits | `ORD-00103` |
| `warehouseId` | Inventory / Warehousing | `warehouses` | `WHS-` + 3 digits | `WHS-001` |
| `stockId` | Inventory / Warehousing | `inventory` | `STK-` + 5 digits | `STK-00031` |
| `shipmentId` | Delivery & Logistics | `shipments` | `SHP-` + 5 digits | `SHP-00045` |
| `reviewId` | Reviews & Ratings | `reviews` | `REV-` + 5 digits | `REV-00230` |
| `transactionId` | Finance & Reporting | `transactions` | `TXN-` + 5 digits | `TXN-00981` |

Rules:

* Inside its **own** collection the ID is stored as **`_id`**. In **another** component's documents it is stored under the field name above. Example: a product is `{ _id: "PRD-00007", ... }`; an order line refers to it as `{ productId: "PRD-00007", ... }`.
* Generate new IDs with the shared `counters` collection (section 3.3), never with random numbers or by counting documents.
* In Java, the getter is named after the field: `getProductId()`, `getCustomerId()`, and so on. Inside the owning class, `Product.getId()` is fine.

## 2. Field naming and types

| Rule | Java type | MongoDB (BSON) type | Example fields |
|---|---|---|---|
| Field names are **camelCase**, in Java and in MongoDB | | | `unitPrice`, `createdAt` |
| Text | `String` | string | `name`, `email` |
| **Money**: GBP (£), always 2 decimal places. **Never `double` or `float`** | `BigDecimal` | `Decimal128` | `price`, `unitPrice`, `totalAmount`, `amount` |
| **Date and time**, always UTC | `java.time.Instant` | `Date` | `createdAt`, `updatedAt`, `orderedAt` |
| Whole numbers, never negative | `int` | int32 | `quantity`, `stockQuantity`, `rating` |
| Yes/no | `boolean` (getter `isActive()`) | boolean, plain name | `active: true`, `visible: false` |
| **Status values**: UPPERCASE text from a fixed list (a Java `enum`), see section 6 | `enum` | string | `"PENDING"`, `"SHIPPED"` |
| Lists | `List<...>` | array | `categories`, `lines` |
| Optional value not set yet | `null` | `null` (keep the field) | `dispatchedAt: null` |

Converting money and dates (copy this):

```java
// write
doc.put("price", new Decimal128(product.getPrice()));          // BigDecimal -> Decimal128
doc.put("createdAt", Date.from(product.getCreatedAt()));       // Instant    -> Date
// read
BigDecimal price = doc.get("price", Decimal128.class).bigDecimalValue();
Instant createdAt = doc.getDate("createdAt").toInstant();
```

Every collection has `createdAt` and `updatedAt` (except where section 4 says otherwise). Set both when you create a record; change `updatedAt` every time you change it.

Never store passwords as plain text. If your component needs a login, store only a hash (field `passwordHash`), and never put real passwords in seed data or in the repository.

## 3. MongoDB

### 3.1 Database and connection

* Database name: **`marketplace`** (the same for everyone).
* Server: **MongoDB Community Server 8.0 or newer**, installed locally on each machine. No Docker, no Atlas. Windows: `net start MongoDB` (or it starts by itself as a service); macOS: `brew services start mongodb-community`. See [TEAM-GUIDE.md](TEAM-GUIDE.md), step 7.
* Connection: **`mongodb://localhost:27017`**, no password (local development only).
* Read the connection from settings, never hard-code it: `mongodb.uri` and `mongodb.database` in your `config/application.properties`, overridden by the environment variables **`MONGODB_URI`** and **`MONGODB_DATABASE`**. Copy how `components/products/.../config/AppConfig.java` does it.
* Use the MongoDB Java driver **`mongodb-driver-sync` 5.14.0** (same version as everyone, see CONTRIBUTING section 7).

### 3.2 Collections and who may write to them

| Collection | Owner (the only component that writes to it) | Holds |
|---|---|---|
| `products` | Products | The product catalogue |
| `customers` | Customers | Customer accounts and addresses |
| `orders` | Orders | Orders, with their order lines embedded |
| `warehouses` | Inventory / Warehousing | Warehouse locations |
| `inventory` | Inventory / Warehousing | Stock of each product in each warehouse |
| `shipments` | Delivery & Logistics | Deliveries and tracking |
| `reviews` | Reviews & Ratings | Ratings and comments |
| `transactions` | Finance & Reporting | Payments and refunds |
| `counters` | Shared: each component updates **only its own** counter document | One document per ID sequence |

* **Only the owner writes** to a collection. Every component may **read** any collection.
* There is **one exception**, so that an order can reserve stock safely: when an order is placed, **Orders** reduces `inventory.quantity` with the single atomic update in section 7 (decision D5). Nobody else writes to another component's collection.
* If you need another component's data changed (for example "mark this order as SHIPPED"), the **owner** provides a method or does it in its own screen. Agree it in the group chat.

### 3.3 The shared `counters` collection

One document per ID sequence; `_id` is the collection name:

```json
{ "_id": "products",  "seq": 15 }
{ "_id": "customers", "seq": 8 }
{ "_id": "orders",    "seq": 10 }
```

Counter `_id` values: `products`, `customers`, `orders`, `warehouses`, `inventory`, `shipments`, `reviews`, `transactions`.

Get the next number with one atomic update, so two users can never get the same ID:

```java
Document doc = counters.findOneAndUpdate(
        Filters.eq("_id", "orders"),
        Updates.inc("seq", 1),
        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
String orderId = String.format("ORD-%05d", ((Number) doc.get("seq")).longValue());
```

(The Products component already does this in `MongoProductRepository.nextProductId()`. Copy the idea, not the class.) The shared seeder moves each counter past the seed data, so IDs created in the app never clash with seed IDs.

### 3.4 Deleting

Other components point at your records, so a delete can leave broken links.

* **Customers, warehouses, orders, transactions:** never hard-delete. Set `active: false` (customers, warehouses) or a final status (`CANCELLED`, `REFUND`).
* **Products:** prefer `active: false`. Only hard-delete a product that no order, review or inventory record refers to.
* **Reviews:** hide with `visible: false` rather than deleting.

## 4. Collection schemas

"Req." = required: the field must always be present. Optional fields may be `null` or missing. The examples are real documents from the shared seed data (`seed-data/`).

### 4.1 `products` (owner: Products)

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `PRD-00001` |
| `name` | String | yes | 1–100 characters |
| `description` | String | yes | May be `""` |
| `price` | Decimal128 | yes | Current selling price, > 0 |
| `stockQuantity` | int | yes | Total stock across all warehouses (see decision D1) |
| `active` | boolean | yes | `false` = not for sale (hidden from customers, cannot be ordered) |
| `categories` | array of String | yes | For example `["Electronics", "Audio"]`; may be empty |
| `imagePaths` | array of String | yes | Paths to image files on the Products user's machine; may be empty |
| `createdAt`, `updatedAt` | Date | yes | |

```json
{ "_id": "PRD-00001", "name": "Wireless Noise-Cancelling Headphones",
  "description": "Over-ear Bluetooth headphones ...", "price": {"$numberDecimal": "129.99"},
  "stockQuantity": 24, "active": true, "categories": ["Electronics", "Audio"], "imagePaths": [],
  "createdAt": {"$date": "2026-10-01T10:00:00Z"}, "updatedAt": {"$date": "2026-10-01T10:00:00Z"} }
```

### 4.2 `customers` (owner: Customers)

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `CUS-00001` |
| `firstName`, `lastName` | String | yes | |
| `email` | String | yes | Lower case, **unique** across all customers |
| `phone` | String | no | UK format, for example `07700 900101` |
| `address` | Address (below) | yes | Default delivery address |
| `active` | boolean | yes | `false` = account closed; cannot place orders |
| `passwordHash` | String | no | Only if you build a login. Never plain text, never in seed data |
| `createdAt`, `updatedAt` | Date | yes | |

**Address** (embedded document, the same shape everywhere: customers, orders, warehouses):

| Field | Type | Req. |
|---|---|---|
| `line1` | String | yes |
| `line2` | String | no (use `""`) |
| `city` | String | yes |
| `postcode` | String | yes (upper case, with the space: `UB8 3PH`) |
| `country` | String | yes (`"United Kingdom"`) |

### 4.3 `orders` (owner: Orders)

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `ORD-00001` |
| `customerId` | String | yes | Must exist in `customers` and be `active` when the order is placed |
| `status` | String | yes | `PENDING`, `PAID`, `SHIPPED`, `DELIVERED`, `CANCELLED` (section 6.1) |
| `lines` | array of OrderLine (below) | yes | At least one line |
| `totalAmount` | Decimal128 | yes | Must equal the sum of all `lineTotal` |
| `deliveryAddress` | Address | yes | **Copy** of the customer's address at the time of ordering |
| `orderedAt` | Date | yes | When the order was placed (use instead of `createdAt`) |
| `updatedAt` | Date | yes | |

**OrderLine** (embedded):

| Field | Type | Req. | Notes |
|---|---|---|---|
| `productId` | String | yes | Must exist in `products` and be `active` when ordered |
| `productName` | String | yes | **Snapshot** of the product name when ordered |
| `unitPrice` | Decimal128 | yes | **Snapshot** of the product price when ordered |
| `quantity` | int | yes | ≥ 1 |
| `lineTotal` | Decimal128 | yes | `unitPrice × quantity` |

Why snapshots? If a product's name or price changes later, old orders must still show what the customer actually bought and paid.

### 4.4 `warehouses` (owner: Inventory / Warehousing)

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `WHS-001` |
| `name` | String | yes | For example `London Central` |
| `address` | Address | yes | |
| `active` | boolean | yes | |
| `createdAt`, `updatedAt` | Date | yes | |

### 4.5 `inventory` (owner: Inventory / Warehousing)

One document per product **per warehouse**.

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `STK-00001` |
| `productId` | String | yes | Must exist in `products` |
| `warehouseId` | String | yes | Must exist in `warehouses` |
| `quantity` | int | yes | Units in this warehouse, never negative |
| `minLevel` | int | yes | Reorder level: show "Low stock" when `quantity <= minLevel` |
| `updatedAt` | Date | yes | (no `createdAt` needed) |

The pair (`productId`, `warehouseId`) is **unique**: never two documents for the same product in the same warehouse.

### 4.6 `shipments` (owner: Delivery & Logistics)

One shipment per order, sent from one warehouse.

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `SHP-00001` |
| `orderId` | String | yes | Must exist in `orders` |
| `warehouseId` | String | yes | Where it is sent from; must exist in `warehouses` |
| `status` | String | yes | `PREPARING`, `DISPATCHED`, `IN_TRANSIT`, `DELIVERED`, `FAILED` (section 6.2) |
| `courier` | String | no | For example `Royal Mail`, `DPD`; `null` until dispatched |
| `trackingNumber` | String | no | `null` until dispatched |
| `dispatchedAt` | Date | no | `null` until dispatched |
| `deliveredAt` | Date | no | `null` until delivered |
| `createdAt`, `updatedAt` | Date | yes | |

### 4.7 `reviews` (owner: Reviews & Ratings)

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `REV-00001` |
| `productId` | String | yes | Must exist in `products` |
| `customerId` | String | yes | Must exist in `customers` |
| `orderId` | String | no | The order that included the product ("verified purchase"); `null` if none |
| `rating` | int | yes | 1 to 5 |
| `title` | String | yes | Short headline |
| `comment` | String | yes | May be `""` |
| `visible` | boolean | yes | `false` = hidden by moderation (not shown, not counted in averages) |
| `createdAt` | Date | yes | (no `updatedAt` needed) |

### 4.8 `transactions` (owner: Finance & Reporting)

| Field | Type | Req. | Notes |
|---|---|---|---|
| `_id` | String | yes | `TXN-00001` |
| `orderId` | String | yes | Must exist in `orders` |
| `customerId` | String | yes | Must exist in `customers` (the order's customer) |
| `type` | String | yes | `PAYMENT` or `REFUND` |
| `amount` | Decimal128 | yes | Always **positive**; `type` says which way the money goes |
| `method` | String | yes | `CARD`, `PAYPAL`, `BANK_TRANSFER` |
| `status` | String | yes | `PENDING`, `COMPLETED`, `FAILED` |
| `createdAt` | Date | yes | (no `updatedAt` needed) |

No real card or bank details are ever stored, only the method.

## 5. Links between components

| From (owner) | Field | Points to | Rule when creating |
|---|---|---|---|
| `orders` | `customerId` | `customers._id` | Customer exists and `active` |
| `orders.lines[]` | `productId` | `products._id` | Product exists and `active`; copy `name` and `price` |
| `inventory` | `productId` | `products._id` | Product exists |
| `inventory` | `warehouseId` | `warehouses._id` | Warehouse exists and `active` |
| `shipments` | `orderId` | `orders._id` | Order is `PAID` |
| `shipments` | `warehouseId` | `warehouses._id` | Warehouse exists |
| `reviews` | `productId` | `products._id` | Product exists |
| `reviews` | `customerId` | `customers._id` | Customer exists |
| `reviews` | `orderId` (optional) | `orders._id` | That order is `DELIVERED`, belongs to the customer and contains the product |
| `transactions` | `orderId` | `orders._id` | Order exists |
| `transactions` | `customerId` | `customers._id` | The same customer as the order |

Before creating a record that points to another one, **read** the other collection and check the rule. Show a clear error message if it fails.

## 6. Status values and how they move

Use exactly these values (as a Java `enum` with these names).

### 6.1 Order (`orders.status`), owned by Orders

```
PENDING ──payment completed──▶ PAID ──shipment dispatched──▶ SHIPPED ──delivered──▶ DELIVERED
   │                             │
   └────────── cancelled ────────┴──▶ CANCELLED   (if it was PAID, Finance adds a REFUND)
```

| Status | Means | What the other components have |
|---|---|---|
| `PENDING` | Placed, not paid yet | No `COMPLETED` payment (a `FAILED` one is possible) |
| `PAID` | Paid, waiting to be sent | A `COMPLETED` `PAYMENT`; a shipment in `PREPARING` |
| `SHIPPED` | On its way | Shipment `DISPATCHED` or `IN_TRANSIT` |
| `DELIVERED` | Received by the customer | Shipment `DELIVERED`; reviews with this `orderId` are allowed |
| `CANCELLED` | Cancelled before dispatch | If it had been paid: a `COMPLETED` `REFUND` for the full amount |

### 6.2 Shipment (`shipments.status`), owned by Delivery

`PREPARING` → `DISPATCHED` → `IN_TRANSIT` → `DELIVERED`, or `FAILED` at any point after `DISPATCHED`.

### 6.3 Transaction, owned by Finance

* `type`: `PAYMENT`, `REFUND`
* `status`: `PENDING`, `COMPLETED`, `FAILED`
* `method`: `CARD`, `PAYPAL`, `BANK_TRANSFER`

Revenue = sum of `COMPLETED` `PAYMENT` amounts minus sum of `COMPLETED` `REFUND` amounts.

## 7. Decisions

These were the open questions in the first draft. They are now decided.

| # | Question | Decision |
|---|---|---|
| D1 | Who owns the stock number? | **Inventory** is the source of truth (`inventory`, per warehouse). `products.stockQuantity` must equal the total of `inventory.quantity` for that product. Products keeps showing it; when Inventory is ready, Products switches to reading the total from `inventory`. The seed data already matches. |
| D2 | Is a SKU a separate field? | **No.** `productId` (`PRD-00007`) is the one product identifier, shown everywhere. |
| D3 | Where are products created? | **Only in the Products component.** Other screens link to it or show products read-only. |
| D4 | Are "Suppliers" in scope? | **No.** They are not one of the seven components. Do not add a suppliers collection. |
| D5 | How does an order reduce stock without overselling? | One atomic update that only succeeds if enough stock remains. If it changes 0 documents, there is not enough stock: show an error and do not place the order. |

D5 in Java (Orders, for each order line, from the order's warehouse):

```java
UpdateResult result = inventory.updateOne(
        Filters.and(Filters.eq("productId", productId),
                    Filters.eq("warehouseId", warehouseId),
                    Filters.gte("quantity", qty)),
        Updates.combine(Updates.inc("quantity", -qty),
                        Updates.set("updatedAt", new Date())));
if (result.getModifiedCount() == 0) {
    // not enough stock: stop and tell the user
}
```

## 8. Seed data

Everyone tests with the **same sample data**, kept in the `seed-data/` folder (one JSON file per collection) and loaded with the shared seeder in `tools/seeder/`. All IDs in it are fixed (`PRD-00001`, `CUS-00001`, ...) and every link points to a real record.

How to load it, check it, and add your own records: **[SEED-DATA-GUIDE.md](SEED-DATA-GUIDE.md)**.

## 9. Change log

| Date | Change | Agreed by |
|---|---|---|
| 2026-11-14 | First version: ID formats, collections, field rules, open questions | Farshad (proposal) |
| 2026-11-20 | Final decision: everyone uses MongoDB Community Server installed locally (no Docker, no Atlas); `docker-compose.yml` removed | Group |
| 2026-11-20 | **Version 1.0, FINAL.** All IDs and collections agreed; full schema for every collection; status values; decisions D1–D5; delete rules; shared seed data and seeder | Farshad (component lead) |
