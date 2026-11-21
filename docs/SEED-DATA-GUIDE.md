# Seed Data Guide

Everyone in the team runs MongoDB on their own computer, so nobody can see anyone else's data. To make sure we all **test with exactly the same data**, the sample data lives in the repository and one shared tool loads it:

| What | Where | What it is for |
|---|---|---|
| Seed files | `seed-data/<collection>.json` | The sample data: one JSON file per collection, fixed IDs, every link pointing to a real record |
| Seeder | `tools/seeder/` | A small Maven project that **checks** the files against [SHARED-CONVENTIONS.md](SHARED-CONVENTIONS.md) and **loads** them into your local `marketplace` database |

After loading, every component sees the same products, customers, orders, stock, shipments, payments and reviews. For example, when Orders shows `ORD-00004` for `CUS-00004`, Delivery sees its shipment `SHP-00004` and Finance sees its payment `TXN-00004`, on every laptop.

Contents: [1. Load the data](#1-load-the-shared-data) · [2. Commands](#2-commands) · [3. Reading the output](#3-reading-the-output) · [4. What is in it](#4-what-is-in-the-seed-data) · [5. Rules](#5-rules-for-seed-data) · [6. Changing your data](#6-adding-or-changing-your-components-seed-data) · [7. In your component](#7-using-the-seed-data-in-your-component) · [8. Troubleshooting](#8-troubleshooting)

---

## 1. Load the shared data

You need JDK 17+, Maven, and MongoDB running (see [TEAM-GUIDE.md](TEAM-GUIDE.md), step 7).

1. **Make sure MongoDB Community Server is running**:
   * Windows: it runs as a service and starts with Windows. If it is stopped: `net start MongoDB` (Command Prompt as administrator)
   * macOS: `brew services start mongodb-community`
   * Check: `mongosh --quiet --eval "db.runCommand({ping:1})"` must print `{ ok: 1 }`
2. **Get the latest seed files:** `git pull` (on your branch, after `git merge main`).
3. **Run the seeder** in a second terminal:

```bash
cd tools/seeder
mvn -q compile exec:java
```

The first time, Maven downloads the MongoDB driver (internet needed). Then you see a table of what was added (section 3). **Run it again whenever seed files change**: it only adds what is missing, so it is always safe to repeat.

**In Eclipse instead:** File > Import > Maven > Existing Maven Projects > `tools/seeder`. Then right-click `SeederMain.java` > **Run As > Java Application**. To pass options such as `--check`: Run > Run Configurations > *SeederMain* > **Arguments** tab > Program arguments.

## 2. Commands

Run these in `tools/seeder`.

| Command | What it does | Needs MongoDB? |
|---|---|---|
| `mvn -q compile exec:java` | Checks the files, then **adds** every seed record that is not in your database yet. Never changes or deletes anything. | Yes |
| `mvn -q compile exec:java -Dexec.args="--check"` | **Only checks** the files against SHARED-CONVENTIONS.md. Run it before every pull request that changes `seed-data/`. | No |
| `mvn -q compile exec:java -Dexec.args="--reset"` | **Deletes everything** in the seeded collections on your computer (including records you made in your app), then loads the seed data fresh. Asks you to type `yes`. | Yes |
| `mvn -q compile exec:java -Dexec.args="--reset --only orders,shipments"` | The same, but only for the collections you name. | Yes |
| `mvn -q compile exec:java -Dexec.args="--help"` | Lists every option. | No |

Other options: `--yes` (skip the `yes` question), `--dir <folder>` (another seed folder), `--uri <uri>` and `--db <name>` (by default the seeder uses `MONGODB_URI` / `MONGODB_DATABASE`, else `mongodb://localhost:27017` and `marketplace`, the same as the components).

**Windows PowerShell:** put the whole option in quotes: `mvn -q compile exec:java "-Dexec.args=--check"`.

## 3. Reading the output

```
Checked 96 documents in 8 files against SHARED-CONVENTIONS.md: 0 error(s), 0 warning(s).

MongoDB:     mongodb://localhost:27017  database "marketplace"

Collection      added  unchanged  different   next new ID
warehouses          3          0          0   WHS-004
products           15          0          0   PRD-00016
customers           8          0          0   CUS-00009
...
```

| Column | Meaning |
|---|---|
| `added` | Seed records that were missing and are now in your database |
| `unchanged` | Already there and identical to the seed file |
| `different` | A record with that ID is already there but is **not** the same as the seed (you edited it in your app, or your app created that ID before you seeded). It is left alone. Use `--reset` if you want the shared version back. |
| `next new ID` | The ID your app will give the next new record. The seeder moves each counter (SHARED-CONVENTIONS 3.3) past the seed IDs, so new records never clash with seed records. |

**ERROR** lines mean a seed file breaks the conventions. **Nothing is written** until every error is fixed. **WARNING** lines are worth reading but do not stop the seeder (for example, a field name that is not in the conventions, which is often a spelling mistake).

## 4. What is in the seed data

| File | Records | Owner (who edits it) | What it covers |
|---|---|---|---|
| `warehouses.json` | 3 | Inventory | `WHS-001` London, `WHS-002` Birmingham, `WHS-003` Manchester |
| `products.json` | 15 | Products | The same 15 products as the Products app's own samples, each with its photo (see below). Two are inactive (`PRD-00009`, `PRD-00014`); two are out of stock (`PRD-00005`, `PRD-00009`); some are low stock |
| `inventory.json` | 33 | Inventory | Where each product's stock is kept, per warehouse. The quantities for a product add up to its `products.stockQuantity`, which Products owns (decision D1) |
| `customers.json` | 8 | Customers | UK addresses, `@example.com` e-mails; `CUS-00008` is inactive |
| `orders.json` | 10 | Orders | Every status: 4 `DELIVERED`, 2 `SHIPPED`, 2 `PAID`, 1 `PENDING`, 1 `CANCELLED`; orders with one and several lines |
| `shipments.json` | 8 | Delivery | One per paid order: `PREPARING`, `IN_TRANSIT`, `DELIVERED` |
| `transactions.json` | 11 | Finance | A `COMPLETED` payment for each paid order, a `FAILED` payment for the pending order, a `REFUND` for the cancelled order |
| `reviews.json` | 8 | Reviews | Ratings 1–5 on delivered orders ("verified purchase"), plus one hidden (`visible: false`) spam review |

The story fits together: each shipment and payment belongs to the right order, each review was written by the customer who received that product, and order totals add up. The checker enforces this, so keep it that way when you add records.

**Product photos.** Each product has one image path such as `images/sample-yoga-mat-6-mm.jpg`. The photo itself is kept in `components/products/src/main/resources/com/marketplace/products/sample-images/yoga-mat-6-mm.jpg`. When the seeder loads products it also copies every photo into `components/products/data/images/` (or `PRODUCTS_DATA_DIR/images` if you set that), which is where the Products app shows images from. The seeder stops with an error if a photo is missing. To change a photo, replace that JPG; to add a product with a photo, add `images/sample-<product-name>.jpg` to its `imagePaths` and the JPG to that folder.

## 5. Rules for seed data

1. **Follow [SHARED-CONVENTIONS.md](SHARED-CONVENTIONS.md) exactly**: collection names, field names, ID formats, status values. The checker tests this.
2. **Fixed IDs, numbered from 1, no gaps**: `ORD-00001`, `ORD-00002`, ... Other people's seed records point at them, so **never renumber or remove** a seed record once it is merged. To retire one, set `active: false` or change its status.
3. **Write MongoDB Extended JSON** for the special types:

   | Type | Write it like this | Not like this |
   |---|---|---|
   | Money | `"price": {"$numberDecimal": "34.95"}` | `"price": 34.95` |
   | Date and time (UTC) | `"createdAt": {"$date": "2026-10-01T09:00:00Z"}` | `"createdAt": "2026-10-01"` |
   | Whole number | `"quantity": 3` | `"quantity": "3"` |
   | Yes/no | `"active": true` | `"active": "true"` |
   | Not set yet | `"dispatchedAt": null` | (leaving the field out) |

4. **Every link must point to a real seed record.** An order's `customerId` must be in `customers.json`, each line's `productId` in `products.json`, and so on (SHARED-CONVENTIONS section 5).
5. **Keep the story consistent** (SHARED-CONVENTIONS section 6): a `DELIVERED` order has a `DELIVERED` shipment and a `COMPLETED` payment; a review's `orderId` must be a delivered order of that customer containing that product; `lineTotal = unitPrice × quantity`; `totalAmount` = sum of the lines.
6. **Small and varied beats big**: about 5–30 records per collection, covering every status and the edge cases you test (zero stock, inactive, cancelled, failed...).
7. **No real personal data and no secrets**: invented names, `@example.com` e-mails, phone numbers from `07700 900000`–`07700 900999` (reserved for fiction), no passwords, no card numbers.
8. **Only edit your own component's file.** If you need a record in someone else's file (for example a customer for your test order), ask the owner.

## 6. Adding or changing your component's seed data

1. Work on your own branch. Edit **only** `seed-data/<your collection>.json` (Inventory owns two files).
2. Copy an existing record and change it. Give a new record the **next** number.
3. Check it (no MongoDB needed):

   ```bash
   cd tools/seeder
   mvn -q compile exec:java -Dexec.args="--check"
   ```

   Fix every ERROR, and read every WARNING.
4. Load it and try it in your app:

   ```bash
   mvn -q compile exec:java -Dexec.args="--reset --only <your collection>"
   ```

5. Commit `seed-data/<your collection>.json`, open a pull request into `main`, and tell the group in the chat ("seed data changed, please pull and run the seeder").

**Your component's file does not exist yet?** Create `seed-data/<collection>.json` with the format above (look at a similar file). Until it exists, the seeder just says "nothing to load for ... yet". The seed files for all eight collections are already there, written to match the conventions, so normally you only adjust yours.

**Need a new field?** Add it to SHARED-CONVENTIONS.md (section 4) **and** to `tools/seeder/src/main/java/com/marketplace/seeder/CollectionRules.java` in the same pull request, then add it to your seed records. Otherwise the checker warns that the field is unknown.

## 7. Using the seed data in your component

* **Test with the seed data**, not your own private samples, so your screens show the same records as everyone else's. Refer to seed records by ID in your test plan ("open `ORD-00004`, status should be `SHIPPED`").
* If your component has **its own seed command** (like Products' `exec:java@seed`), it must produce the same records as your seed file, or simply tell people to use the shared seeder.
* Read other components' collections freely, but **write only to your own** (SHARED-CONVENTIONS 3.2).
* Unit tests should not depend on MongoDB. For integration tests, use a separate database name (for example `marketplace_test`), never the shared `marketplace` database. Your tests must never delete the seed data.

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `ERROR: cannot use MongoDB at mongodb://localhost:27017` | MongoDB is not running. Windows: `net start MongoDB`; macOS: `brew services start mongodb-community`. Check with `mongosh --eval "db.runCommand({ping:1})"`. |
| `cannot find the seed-data folder` | Run the seeder from `tools/seeder` inside the repository, or pass `--dir`. |
| `is not valid JSON` | A missing comma, quote or bracket in that file. The message gives the position. Paste the file into a JSON validator if needed. |
| `must be money written as {"$numberDecimal": ...}` | You wrote a plain number. See rule 3 in section 5. |
| `does not exist in customers.json` (or another file) | The ID you point to is not in that seed file. Use an existing ID or ask the owner to add one. |
| Products in the Products app show "Image unavailable" | Run the seeder from `tools/seeder` (it prints `Product photos: ... copied`). If you seeded before the photos existed, run once with `--reset --only products`. |
| Many `different` in the table | Your database has your own edits. Run with `--reset` (and `--only ...`) to get the shared data back. |
| `Could not create an index on customers` | Your database has two customers with the same e-mail (made in your app). Fix them, or `--reset --only customers`. |
| Maven: `Could not resolve dependencies` | The first run needs internet to download the MongoDB driver. |
