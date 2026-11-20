# Marketplace: Products Component (NC1605)

> **Academic coursework notice:** this component is part of the **NC1605 group coursework** for **Computer Science (Year 1) at Brunel University of London** (student 2408546) and is submitted for assessment. It is public only so the team can collaborate and the work can be reviewed. Do not copy, reuse or submit any of this code or documentation as your own work.

A local desktop application for managing the product catalogue of an e-commerce marketplace.
It is the **Products** component only (component 1 of 7 in the NC1605 brief).

* **Java 17+**, **JavaFX 21** (FXML + CSS), **MongoDB** for storage, **Maven** to build and test, **JUnit 5** for tests.
* Add, edit, deactivate and delete products; search and filter; several categories and several images per product.
* Product IDs look like `PRD-00007`, never change, and are ready for Orders / Reviews / Inventory to link to.

## UI design (Figma)

* **[Interactive prototype](https://www.figma.com/proto/uJmMPHItOGvXNZSgwFZb2k/NC1605-Products-UI---Farshad-2408546?node-id=1-845&starting-point-node-id=1-845&scaling=contain)**: a click-by-click demo of the Products screens. Start on the cover and click **Start the demo**. The path is: add a product (with validation errors), search, no results, edit, delete, and the database-error state with retry.
* **[Figma design file](https://www.figma.com/design/uJmMPHItOGvXNZSgwFZb2k/NC1605-Products-UI---Farshad-2408546)**: all 13 screens plus the style guide (colours, type, buttons, status badges, shared names). It uses the same Zyno look as the Inventory / Warehousing screens.

Both links are view-only for anyone with the link.

The JavaFX screens follow this design:

* **Product catalogue:** sidebar, page header with the database status pill, four summary cards (total, in stock, low stock, out of stock), the product list with search and filters, and the selected product panel with Edit, Deactivate and Delete.
* **Add / edit product:** a details card (name, price, stock, description, category chips, active) and an images card, with errors shown under each field and in a banner at the top.
* **States:** loading, empty catalogue, no search results with **Show all products**, the delete confirmation, toast messages after each action, and the **Database problem** panel with **Retry connection**.

| Document | What it is |
|---|---|
| [docs/01-requirements-checklist.md](docs/01-requirements-checklist.md) | Numbered requirements, where each is implemented, how it is verified |
| [docs/02-manual-test-plan.md](docs/02-manual-test-plan.md) | Step-by-step manual tests with columns for results and screenshots |
| [docs/03-design-notes.md](docs/03-design-notes.md) | Architecture, MongoDB schema, image storage, integration points |
| [docs/04-diagrams.md](docs/04-diagrams.md) | Mermaid class diagram and flowcharts (names match the code) |

---

## 1. What you need installed

| Tool | Version | Notes |
|---|---|---|
| **JDK** | **17 or newer** | The code is written for Java 17 and also runs on newer JDKs. Use a full JDK, not just a JRE. |
| **Maven** | 3.9 or newer | Or use Eclipse's built-in Maven (m2e). Check with `mvn -v`. |
| **MongoDB Community Server** | 8.0 or newer | Installed locally (section 3). The group decision is final: no Docker, no Atlas. |
| MongoDB Shell (`mongosh`) | any | Only to check the server and look at the data. |

The first build downloads JavaFX, the MongoDB driver and JUnit from Maven Central, so it needs an internet connection once.
Maven chooses the correct JavaFX files for Windows, macOS or Linux automatically.

---

## 2. Quick start

Make sure MongoDB Community Server is running (section 3), then run the `mvn` commands in this folder (`components/products`, the folder containing `pom.xml`).

```bash
# 1. MongoDB Community Server must be running (Windows: it starts as a service; macOS: brew services start mongodb-community)
mvn test                             # 2. optional: run the unit tests (no database needed)
mvn compile exec:java@seed           # 3. optional: load ~15 sample products (safe to repeat)
mvn javafx:run                       # 4. start the application
```

That is all: with the defaults the app connects to `mongodb://localhost:27017`, database `marketplace`.

---

## 3. MongoDB setup

We use **MongoDB Community Server** installed on each computer (group decision, final: no Docker, no Atlas).

**Install (once)**

* **Windows:** download the MSI installer from https://www.mongodb.com/try/download/community (version 8.0 or newer), choose *Complete* and keep **"Install MongoD as a Service"** ticked. MongoDB then starts by itself whenever Windows starts.
* **macOS (Homebrew):** `brew tap mongodb/brew && brew install mongodb-community`
* Also install **MongoDB Shell** (`mongosh`) from https://www.mongodb.com/try/download/shell to run the check below.

**Start and stop**

| | Windows (Command Prompt as administrator) | macOS |
|---|---|---|
| Start | `net start MongoDB` | `brew services start mongodb-community` |
| Stop | `net stop MongoDB` | `brew services stop mongodb-community` |

MongoDB then listens on `localhost:27017` with no password, which is exactly what the default settings use, so no configuration is needed.
If your server is set up with a login, do **not** put the password in any project file: set the full connection string in the `MONGODB_URI` environment variable instead (for example `set MONGODB_URI=mongodb://USER:PASSWORD@localhost:27017/` in Windows cmd).

### Check that MongoDB is reachable

```bash
mongosh --quiet --eval "db.runCommand({ping:1})"
```

You should see `{ ok: 1 }`. If MongoDB is not running the application does **not** fall back to a fake database:
it shows a "Database problem" panel with instructions and a **Retry connection** button.

---

## 4. Configuration

Settings are read in this order: **environment variable, then `config/application.properties`, then the built-in default.**

| Setting | Environment variable | Properties key | Default |
|---|---|---|---|
| MongoDB connection string | `MONGODB_URI` | `mongodb.uri` | `mongodb://localhost:27017` |
| Database name | `MONGODB_DATABASE` | `mongodb.database` | `marketplace` |
| Folder for uploaded images | `PRODUCTS_DATA_DIR` | `data.dir` | `data` (next to where you start the app) |
| Config file location | `PRODUCTS_CONFIG_FILE` | n/a | `config/application.properties` |

To use a file: copy `config/application.properties.example` to `config/application.properties` and edit the copy.
The copy is git-ignored. The example contains no credentials.

---

## 5. Running the application

### With Maven

```bash
mvn javafx:run
```

### From Eclipse

Right-click `src/main/java/com/marketplace/products/Launcher.java` > **Run As > Java Application**.
Always start from **`Launcher`**, not `MainApp`: a class that extends `Application` cannot be started directly when JavaFX is
on the classpath (you get *"JavaFX runtime components are missing"*). `Launcher` exists to avoid that error.

### Console warnings on newer JDKs

On JDK 24 or newer you will see harmless lines such as `WARNING: A restricted method in java.lang.System has been called`
and `sun.misc.Unsafe ... will be removed in a future release`. They come from JavaFX 21 itself, not from this project.
They disappear on JDK 17 or 21.

---

## 6. Loading sample data (the seed command)

Nothing is seeded automatically. To load about 15 varied sample products (with locally generated placeholder pictures):

```bash
mvn compile exec:java@seed
```

or in Eclipse: run **`SeedMain`** (`src/main/java/com/marketplace/products/SeedMain.java`) as a Java Application.

* It never overwrites anything: a sample whose name already exists is **skipped**.
* Running it again adds **0** products ("skipped 15 that already existed").
* If a sample was deleted, the next run adds just that one back.
* The samples include inactive, out-of-stock, low-stock, multi-category, multi-image and no-image products so every UI state can be demonstrated.

If MongoDB is unreachable it prints `SEED FAILED: Cannot reach MongoDB ...` and exits with code 1.

---

## 7. Testing

```bash
mvn test                       # 169 unit tests (166 plain + 3 FXML wiring tests): fast, no database needed
mvn verify -Pintegration       # unit tests + 11 integration tests that need a running MongoDB
mvn -Dtest=ProductValidatorTest test        # run one test class
```

| Kind | Files | Needs MongoDB? | Notes |
|---|---|---|---|
| Unit tests | `src/test/java/.../*Test.java` | No | Validation, search and filters, stable IDs, activation, images, seed idempotency, mapper (Decimal128), config |
| FXML wiring tests | `ui/FxmlLoadTest.java` | No, but needs a graphical session | Loads both FXML files and checks every `@FXML` field has a matching `fx:id`. **Skipped** (not passed) if JavaFX cannot start |
| Integration tests | `src/test/java/.../*IT.java` | **Yes** | Run only with `-Pintegration`. Use a temporary database `marketplace_it_xxxxxxxx` that is dropped afterwards, so your real data is never touched. **Skipped** (reported as skipped) if MongoDB is not reachable |

The tests use `InMemoryProductRepository`, a small fake that exists **only in `src/test`**. The real application never uses an in-memory database.

In Eclipse: right-click a test class or the `src/test/java` folder > **Run As > JUnit Test**.

---

## 8. Eclipse import and submission

### Import

1. Install a **JDK 17 or newer** (for example Eclipse Temurin) and **Eclipse IDE for Java Developers** (it includes Maven support, "m2e"). On Windows, unzip the project to a short folder such as `C:\dev\` (avoid very long paths).
2. **File > Import... > Maven > Existing Maven Projects > Next**, browse to this folder, tick `pom.xml`, **Finish**.
   (The folder also contains Eclipse's `.project`, `.classpath` and `.settings` files, so **File > Import > General > Existing Projects into Workspace** works too. If anything looks wrong, delete those files and use the Maven import above, which regenerates them.)
3. Wait for the dependency download (progress bar bottom-right).
4. Make sure Eclipse uses a JDK 17+: **Window > Preferences > Java > Installed JREs** (add a JDK if only a JRE is listed), and right-click the project > **Properties > Java Compiler** > compliance 17 or higher.
5. If you see red errors after import: right-click the project > **Maven > Update Project... > OK**.
6. Run `Launcher` (application), `SeedMain` (seed command), or `src/test/java` as JUnit (tests).

### Export for submission (as the brief describes)

1. **File > Export > General > File System > Next**, select the project folder.
2. Make sure `src`, `pom.xml`, `docs`, `config` and the Eclipse files (`.project`, `.classpath`, `.settings`) are ticked. `bin`/`target` may be included or left out, since Maven rebuilds them.
3. Compress the exported folder into `<StudentID>_NC1605_Implementation_Code.zip`.

Notes for the examiner: MongoDB must be running on the examiner's machine
(MongoDB Community Server on `localhost:27017`), and the first run needs internet to download libraries.
The submission does **not** include a folder of `.jar` libraries: JavaFX's native files differ per operating system, and Maven downloads the right ones on each machine.
`config/application.properties` is never included if it holds a real password.

---

## 9. Testing

* **Unit tests:** `mvn test` runs 169 tests (166 plain + 3 FXML wiring tests). They need no database.
* **Integration tests:** `mvn verify -Pintegration` adds 11 tests against a real MongoDB, using a temporary database that is dropped afterwards.
* **Manual tests:** [docs/02-manual-test-plan.md](docs/02-manual-test-plan.md) covers the screens step by step (search, filters, add, edit, images, deactivate, delete, database errors, Windows and Eclipse), with a column for each result and screenshot.
* **Requirements:** [docs/01-requirements-checklist.md](docs/01-requirements-checklist.md) links every requirement to its code, automated test and manual test.

---

## 10. Project layout

```
pom.xml                          Maven build (Java 17, JavaFX 21, MongoDB driver, JUnit 5)
config/application.properties.example
docs/                            requirements, test plan, design notes, diagrams
src/main/java/com/marketplace/products/
    Launcher.java  MainApp.java  SeedMain.java  AppContext.java
    model/        Product, ProductFilter, ProductForm, Categories, StockLevel
    validation/   ProductValidator, ValidationResult, ValidationException
    repository/   ProductRepository (interface), MongoProductRepository, ProductMapper, DatabaseException
    service/      ProductService, ProductSearch, SearchResult, ImageStorageService, SampleDataSeeder, PlaceholderImageGenerator, ...
    config/       AppConfig
    ui/           MainController, ProductFormController, AsyncRunner, ProductImageLoader, Dialogs, Formatters
src/main/resources/com/marketplace/products/ui/   main-view.fxml, product-form.fxml, application.css
src/test/java/...                unit tests (*Test) and MongoDB integration tests (*IT)
```

Where the data lives: product documents in MongoDB database `marketplace`, collection `products` (plus a tiny `counters` collection for ID numbers);
picture files in `data/images/` (a folder of random-named files, referenced from MongoDB as `images/<name>`).

---

## 11. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| *Error: JavaFX runtime components are missing* | You started `MainApp` directly. Run **`Launcher`** instead. |
| Red "Database problem" panel: *Cannot reach MongoDB* | MongoDB Community Server is not running or the URI is wrong. Start it (Windows: `net start MongoDB`; macOS: `brew services start mongodb-community`), then press **Retry connection**. Check `MONGODB_URI`. |
| `net start MongoDB` says the service name is invalid | MongoDB was installed without the service option. Run the installer again and tick "Install MongoD as a Service". |
| *MongoDB rejected the username or password* | Fix the credentials in `MONGODB_URI`. |
| Pictures show "Image unavailable" | The image file is missing from `data/images`. You started the app from a different folder (set `PRODUCTS_DATA_DIR` to one fixed absolute path), or the file was deleted. |
| Eclipse: *release version 17 not supported* | Eclipse is using an older JRE. Add a JDK 17+ under Preferences > Java > Installed JREs and select it for the project. |
| Eclipse: red `javafx` imports | Right-click project > Maven > Update Project (force update). |
| `mvn: command not found` | Install Maven, or use Eclipse (Run As > Maven build... goals: `javafx:run`). |
| Console lines starting `WARNING: A restricted method...` | Harmless JavaFX 21 on JDK 24+ notices (see section 5). |
