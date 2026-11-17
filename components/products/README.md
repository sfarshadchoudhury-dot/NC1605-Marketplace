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

| Document | What it is |
|---|---|
| [docs/01-requirements-checklist.md](docs/01-requirements-checklist.md) | Numbered requirements, where each is implemented, how it is verified |
| [docs/02-manual-test-plan.md](docs/02-manual-test-plan.md) | Step-by-step manual tests with space for your results and screenshots |
| [docs/03-design-notes.md](docs/03-design-notes.md) | Architecture, MongoDB schema, image storage, integration points |
| [docs/04-diagrams.md](docs/04-diagrams.md) | Mermaid class diagram and flowcharts (names match the code) |

---

## 1. What you need installed

| Tool | Version | Notes |
|---|---|---|
| **JDK** | **17 or newer** | The code is written for Java 17. Tested on JDK 17, 21 and 25 (see [section 9](#9-what-has-and-has-not-been-verified)). Use a full JDK, not just a JRE. |
| **Maven** | 3.9 or newer | Or use Eclipse's built-in Maven (m2e). Check with `mvn -v`. |
| **MongoDB** | 6.0 or newer | Easiest with Docker (below). Tested against MongoDB 7.0. |
| Docker Desktop | optional | Only needed for the one-line MongoDB start-up. |

The first build downloads JavaFX, the MongoDB driver and JUnit from Maven Central, so it needs an internet connection once.
Maven chooses the correct JavaFX files for Windows, macOS or Linux automatically.

---

## 2. Quick start (5 commands)

Run `docker compose` from the **repository root** (the shared MongoDB for all components); run the `mvn` commands in this folder (`components/products`, the folder containing `pom.xml`).

```bash
docker compose up -d                 # 1. start MongoDB (skip if you already run MongoDB)
mvn test                             # 2. optional: run the unit tests (no database needed)
mvn compile exec:java@seed           # 3. optional: load ~15 sample products (safe to repeat)
mvn javafx:run                       # 4. start the application
```

That is all: with the defaults the app connects to `mongodb://localhost:27017`, database `marketplace`.

---

## 3. MongoDB setup

Pick **one** option.

### Option A: Docker Compose (recommended)

```bash
# run these from the repository root (the shared docker-compose.yml lives there)
docker compose up -d        # start (data survives restarts in the "mongo_data" volume)
docker compose ps           # check it says "running"
docker compose down         # stop (data kept)
docker compose down -v      # stop AND delete all data
```

The container listens on `127.0.0.1:27017` only and has **no password**, so use it for local development only.

### Option B: Install MongoDB Community Server

* **macOS (Homebrew):** `brew tap mongodb/brew && brew install mongodb-community && brew services start mongodb-community`
* **Windows:** download the MSI installer from mongodb.com/try/download/community, tick "Install MongoDB as a Service", finish the wizard.

Either way MongoDB then listens on `localhost:27017` and the default settings work.

### Option C: MongoDB Atlas or a server with a password

Do **not** put the password in any file you will submit. Set it as an environment variable instead:

```bash
# macOS / Linux
export MONGODB_URI="mongodb+srv://USER:PASSWORD@cluster0.example.mongodb.net/"

# Windows PowerShell
$env:MONGODB_URI = "mongodb+srv://USER:PASSWORD@cluster0.example.mongodb.net/"

# Windows cmd
set MONGODB_URI=mongodb+srv://USER:PASSWORD@cluster0.example.mongodb.net/
```

(Only the unauthenticated local container has been tested; see [section 9](#9-what-has-and-has-not-been-verified).)

### Check that MongoDB is reachable

```bash
docker exec marketplace-mongodb mongosh --quiet --eval "db.runCommand({ping:1})"     # Docker option
mongosh --quiet --eval "db.runCommand({ping:1})"                                       # local install
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
   (The folder also contains Eclipse's `.project`, `.classpath` and `.settings` files, so **File > Import > General > Existing Projects into Workspace** works too. Those files were written by hand in the standard m2e format and **have not been tried in a real Eclipse**; if anything looks wrong, delete them and use the Maven import above, which regenerates them.)
3. Wait for the dependency download (progress bar bottom-right).
4. Make sure Eclipse uses a JDK 17+: **Window > Preferences > Java > Installed JREs** (add a JDK if only a JRE is listed), and right-click the project > **Properties > Java Compiler** > compliance 17 or higher.
5. If you see red errors after import: right-click the project > **Maven > Update Project... > OK**.
6. Run `Launcher` (application), `SeedMain` (seed command), or `src/test/java` as JUnit (tests).

### Export for submission (as the brief describes)

1. **File > Export > General > File System > Next**, select the project folder.
2. Make sure `src`, `pom.xml`, `docs`, `config`, a copy of the repository's root `docker-compose.yml`, and the Eclipse files (`.project`, `.classpath`, `.settings`) are ticked. `bin`/`target` may be included or left out, since Maven rebuilds them.
3. Compress the exported folder into `<StudentID>_NC1605_Implementation_Code.zip`.

Notes for the examiner (put a short copy in your report): MongoDB must be running on the examiner's machine
(`docker compose up -d`, or a local install), and the first run needs internet to download libraries.
Do **not** zip a folder of `.jar` libraries copied from your Mac to submit to a Windows machine: JavaFX's native files differ per operating system. Maven is what makes the project portable.
Do not include `config/application.properties` if you created one with a real password.

---

## 9. What has and has not been verified

**Verified (by running it, on macOS arm64, 30 Oct 2026):**

* `mvn clean verify -Pintegration` passes on **JDK 17, 21 and 25**: 169 unit tests + 11 integration tests against MongoDB 7.0 in Docker, 0 failures, 0 skipped.
* The seed command twice against real MongoDB: 15 added, then 0 added / 15 skipped; prices stored as `Decimal128`; counter and indexes created.
* `mvn javafx:run` starts the real application with no exceptions (JDK 25).
* A scripted GUI run drove the real controllers against real MongoDB: load, search, price-box error, combined filter, reset, add form (empty, invalid and valid input), deactivate, delete with Cancel then confirm, edit keeping the ID, the empty-catalogue state, MongoDB-down state, and losing then regaining the database mid-session. Scene snapshots were inspected.

**Not verified:**

* **Windows** (nothing here was run on Windows) and **Eclipse** (import and run were not tried).
* Picking images through the **native file-chooser dialog** (it cannot be scripted). The copy / validation behind it is unit-tested, but clicking through it, and the **X** remove button on form thumbnails, still need a manual check (tests M-14, M-19).
* Sorting by clicking column headers and double-click-to-edit (tests M-05, M-23).
* MongoDB with a username/password, or Atlas. The error message for rejected credentials exists but was never triggered.
* Everything in the manual test plan marked "Actual: ____": **you must run those and capture your own screenshots.**

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
| Red "Database problem" panel: *Cannot reach MongoDB* | MongoDB is not running or the URI is wrong. `docker compose up -d`, then press **Retry connection**. Check `MONGODB_URI`. |
| `docker compose up` says port 27017 is already in use | Another MongoDB is running. Stop it, or use it (the defaults already point at port 27017). |
| *MongoDB rejected the username or password* | Fix the credentials in `MONGODB_URI`. |
| Pictures show "Image unavailable" | The image file is missing from `data/images`. You started the app from a different folder (set `PRODUCTS_DATA_DIR` to one fixed absolute path), or the file was deleted. |
| Eclipse: *release version 17 not supported* | Eclipse is using an older JRE. Add a JDK 17+ under Preferences > Java > Installed JREs and select it for the project. |
| Eclipse: red `javafx` imports | Right-click project > Maven > Update Project (force update). |
| `mvn: command not found` | Install Maven, or use Eclipse (Run As > Maven build... goals: `javafx:run`). |
| Console lines starting `WARNING: A restricted method...` | Harmless JavaFX 21 on JDK 24+ notices (see section 5). |
