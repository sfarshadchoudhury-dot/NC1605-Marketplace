# 1. Requirements checklist

Source: the NC1605 Assessment B brief (Products component, page 2) plus the extra project rules for this build.
The brief wants every requirement marked **Complete** or **Incomplete** *after testing*, with an explanation for anything incomplete.

How to read the columns

* **Implemented**: the code for it exists (where to find it is in the "Where" column).
* **Verified by me**: what I actually ran while building this (automated test classes, or my scripted GUI run). This is **not** your evidence.
* **Your manual test**: the test in [02-manual-test-plan.md](02-manual-test-plan.md) you must run and screenshot yourself.
* **Final status**: left blank on purpose. Fill in **Complete / Incomplete** after you have run the manual tests. If Incomplete, add what is missing and how to finish it.

"Scripted GUI run" means a throwaway program I wrote that clicked the real buttons of the real controllers against a real MongoDB and saved snapshots of the window. It was not a human using the app.

## A. Functional requirements from the brief

| ID | Requirement | Where it is implemented | Implemented | Verified by me | Your manual test | Final status |
|---|---|---|---|---|---|---|
| R1 | Display the catalogue with images, name, categories, description, price, stock and active/inactive status | `MainController.configureTable`, `ProductImageLoader`, `main-view.fxml` | Yes | Scripted GUI run (all columns rendered) | M-02, M-04, M-06 | |
| R2 | Add products with unique, stable IDs | `ProductService.addProduct`, `MongoProductRepository.nextProductId` (atomic counter, `PRD-00001` format) | Yes | `ProductServiceTest` (unique IDs, no ID used by a rejected form); `MongoProductRepositoryIT` (sequential IDs, 40 threads get 40 different IDs) | M-07 | |
| R3 | Edit all product details without changing the ID | `ProductService.updateProduct` (`Product.id` is `final`, no setter); `ProductFormController` | Yes | `ProductServiceTest.editingChangesEveryDetailButNeverTheId`, `editingManyTimesKeepsTheSameId`; `MongoProductRepositoryIT.editKeepsTheSameDocumentIdAndDoesNotCreateANewDocument`; scripted GUI run (edit kept `PRD-00003`) | M-23, M-24 | |
| R4 | Deactivate products (and reactivate) | `ProductService.setActive`, `MainController.onToggleActive` | Yes | `ProductServiceTest` (deactivate, reactivate, stays in catalogue, stock untouched); scripted GUI run | M-25 | |
| R5 | Delete products, with a confirmation dialogue | `ProductService.deleteProduct`, `MainController.onDeleteProduct`, `Dialogs.confirmDelete` | Yes | `ProductServiceTest` (delete + image cleanup); scripted GUI run (Cancel kept the product, confirm removed it) | M-26, M-27 | |
| R6 | Search by product name | `ProductSearch.matchesName` (case-insensitive "contains") | Yes | `ProductSearchTest` (case, spaces, no match) | M-28 | |
| R7 | Combine filters: category, price range, availability | `ProductSearch.matches` (all filters AND-ed), `MainController.buildFilter` | Yes | `ProductSearchTest` (single and combined filters, inclusive price bounds); `MongoProductRepositoryIT.searchWorksOnRealData`; scripted GUI run | M-29 to M-33 | |
| R8 | Assign one or more categories to each product | `Product.categories`, form check-boxes + "Add category", `Categories`, `ProductValidator.validateCategories` | Yes | `ProductValidatorTest`, `ProductServiceTest` (duplicates cleaned); scripted GUI run (custom category) | M-07, M-12, M-13 | |
| R9 | Upload multiple images per product | `ProductFormController.onAddImages` / `importImages`, `ImageStorageService.storeImage`, limit 6 | Yes | `ImageStorageServiceTest` (copy logic); **GUI file-chooser click-through not verified** | M-14, M-18 | |
| R10 | Preview images | Big preview + thumbnails in the form and the details panel | Yes | Scripted GUI run (preview + thumbnails rendered for stored images); **adding images via the dialog not verified** | M-06, M-14 | |
| R11 | Display images in the catalogue | Thumbnail column, `ProductImageLoader.createView` | Yes | Scripted GUI run | M-04 | |
| R12 | Remove images | `ProductFormController.removeImage`; `ProductService.updateProduct` deletes files for removed images | Yes | `ProductServiceTest.removingAnImageInTheEditFormDeletesItsFileOnSave`; **X button not clicked in my GUI run** | M-19 | |
| R13 | Name must be present and non-blank | `ProductValidator.validateName` | Yes | `ProductValidatorTest` (null, empty, spaces, tab, newline; length limit); scripted GUI run (`"   "` rejected) | M-08, M-09 | |
| R14 | Price must be a positive number | `ProductValidator.validatePrice` | Yes | `ProductValidatorTest` (0, negative, text, 3 decimals, `1e3`, `NaN`, `$5`, ...) | M-10 | |
| R15 | Stock must be a non-negative integer | `ProductValidator.validateStock` | Yes | `ProductValidatorTest` (negative, fractional, text, too large, ...) | M-11 | |
| R16 | Validate all input; show clear field-level errors; block invalid submission | `ProductValidator.validate`, `ValidationResult`, `ProductFormController.showValidation`; the service validates again | Yes | `ProductValidatorTest`, `ProductServiceTest.invalidProductIsNotSavedAndDoesNotUseUpAnId`; scripted GUI run (message under each field) | M-08 to M-13, M-24, M-30 | |
| R17 | Persist in MongoDB; saved products load after restart | `MongoProductRepository`, `ProductMapper` | Yes | `MongoProductRepositoryIT.productsAreStillThereAfterTheApplicationRestarts` | M-36 | |
| R18 | Other components can reference products by unique product ID | `_id` = `PRD-nnnnn`; [design notes section 6](03-design-notes.md#6-integration-points-for-the-other-components) | Yes (documented; nothing invented for other components) | Document review | M-43 | |
| R19 | Orders, Reviews and Inventory must be able to link to products | same as R18 | Documented only (the other components are teammates' work) | n/a | Group integration evidence: **yours to add** | |
| R20 | Validation messages clear; interface supports view, sort and edit efficiently | Sortable table columns, double-click to edit, details panel | Yes | Scripted GUI run (layout); **sorting and double-click not verified** | M-05, M-23 | |

## B. Design and data rules

| ID | Requirement | Where | Implemented | Verified by me | Your manual test | Final status |
|---|---|---|---|---|---|---|
| R21 | Separate models, FXML controllers, services, validation and repositories | Packages `model`, `ui` (+ FXML), `service`, `validation`, `repository` | Yes | Code review | M-43 | |
| R22 | `BigDecimal` for prices, MongoDB `Decimal128` for storage, no floating-point money maths | `Product.price`, `ProductMapper`, `ProductValidator.toPrice` | Yes | `ProductMapperTest` (Decimal128, exact 0.10 + 0.20); `MongoProductRepositoryIT` (raw document holds `Decimal128`); seed run inspected in `mongosh` | M-21 | |
| R23 | Document the collection, fields, IDs and indexes | [03-design-notes.md section 3](03-design-notes.md#3-mongodb-schema) | Yes | Indexes `name_1`, `categories_1`, `price_1` seen in `mongosh` and checked by `MongoProductRepositoryIT.indexesAreCreatedAtConnectTime` | n/a | |
| R24 | Implement only Products; document integration points | [design notes section 6](03-design-notes.md#6-integration-points-for-the-other-components) | Yes | Document review | n/a | |
| R25 | Product stock kept per the brief; document future warehouse-stock integration | `Product.stockQuantity`; [design notes section 7](03-design-notes.md#7-stock-and-the-future-inventory-component) | Yes | Document review | n/a | |

## C. Image handling

| ID | Requirement | Where | Implemented | Verified by me | Your manual test | Final status |
|---|---|---|---|---|---|---|
| R26 | Copy selected images into an application-managed data directory | `ImageStorageService.storeImage` (`<data dir>/images/<uuid>.<ext>`) | Yes | `ImageStorageServiceTest` (copy; original untouched; survives deleting the original) | M-14, M-21 | |
| R27 | No dependence on original absolute paths; portable relative references | Stored as `images/<uuid>.<ext>`; `ImageStorageService.resolve` | Yes | `ImageStorageServiceTest.referenceIsRelativeAndPortable`, `movingTheWholeDataFolderKeepsReferencesWorking`; seeded document inspected in `mongosh` | M-21 | |
| R28 | Validate supported formats and file sizes | `ImageStorageService` (extension list, 5 MB limit, file signature check) | Yes | `ImageStorageServiceTest` (28 tests: types, empty, oversized, renamed text file, case) | M-15, M-16, M-17 | |
| R29 | Handle missing or invalid images gracefully | `ProductImageLoader` ("No image" / "Image unavailable" placeholder) | Yes | Scripted GUI run showed the "No image" state for a product without images; **a deleted file on disk not tried** | M-22 | |
| R30 | A few locally generated placeholder images for sample products | `PlaceholderImageGenerator` (Java2D, no downloads), used by `SampleDataSeeder` | Yes | `SampleDataSeederTest`; seed run (21 PNG files created) | M-02 | |

## D. User interface

| ID | Requirement | Where | Implemented | Verified by me | Your manual test | Final status |
|---|---|---|---|---|---|---|
| R31 | Clean, modern interface: table, search/filter with reset, add/edit form, multi-image previews, details view, active/inactive and stock indicators | `main-view.fxml`, `product-form.fxml`, `application.css` | Yes | Scripted GUI run; snapshots inspected | M-04, M-06, M-34 | |
| R32 | Empty, loading, validation, success and database-error states | `MainController.showView`, `handleFailure`, `showMessage`; form error labels | Yes | Scripted GUI run: empty state, database-down state, validation errors, success messages, loss of MongoDB mid-session and recovery. **Loading spinner is only visible for about a second; not captured** | M-01, M-08, M-37, M-38 | |
| R33 | Confirmation before destructive actions | `Dialogs.confirmDelete` (Cancel is the default button) | Yes | Scripted GUI run | M-26 | |
| R34 | Database and file operations off the JavaFX thread; UI updates back on that thread | `AsyncRunner` (JavaFX `Task`, one worker thread); `ProductImageLoader` uses `backgroundLoading` | Yes | Code review only. **Responsiveness while MongoDB is slow or down was not measured.** | M-40 | |

## E. Setup and data

| ID | Requirement | Where | Implemented | Verified by me | Your manual test | Final status |
|---|---|---|---|---|---|---|
| R35 | Connection settings from environment variables or a local config file; example without credentials | `AppConfig`, `config/application.properties.example` | Yes | `AppConfigTest` (priority order, blank values, masking, the example file contains no credentials) | M-39 | |
| R36 | Clear MongoDB setup instructions and optional Docker Compose file | `README.md` section 3, `docker-compose.yml` (repository root, shared by all components) | Yes | Container started from the file and used for all my runs | M-01 | |
| R37 | If MongoDB is unavailable: actionable error, never a silent in-memory fallback | `AppContext.connect`, `MongoProductRepository.translate`, `MainController.handleFailure` | Yes | `AppContextTest`; scripted GUI run (MongoDB down at start, and stopped mid-session) | M-37, M-38 | |
| R38 | Explicit seed command, about 15 varied products, no overwrite, no duplicates on re-run | `SeedMain`, `SampleDataSeeder` (`mvn compile exec:java@seed`) | Yes | `SampleDataSeederTest` (6 tests); `MongoProductRepositoryIT.seedingTwiceCreatesNoDuplicatesInMongo`; real run: added 15, then 0 | M-02, M-03 | |

## F. Testing, documentation and the viva

| ID | Requirement | Where | Implemented | Verified by me | Your manual test | Final status |
|---|---|---|---|---|---|---|
| R39 | JUnit tests for: required fields and whitespace names; zero/negative/invalid prices; negative/fractional/invalid stock; combined search and filters; stable IDs on edit; activation/deactivation; image validation | `src/test/java` (169 unit tests) | Yes | All pass on JDK 17, 21 and 25 (macOS) | Re-run and screenshot (see test plan, "Automated test index") | |
| R40 | Database integration tests kept separate and documented | `*IT.java`, Maven profile `integration`, README section 7 | Yes | 11 pass against MongoDB 7.0 in Docker | Re-run with `mvn verify -Pintegration` | |
| R41 | Documentation set (README, checklist, test plan, design notes, diagrams) | `README.md`, `docs/` | Yes | Mermaid diagrams: all 30 blocks (29 in `04-diagrams.md`, 1 in the design notes) rendered without syntax errors using mermaid-cli | n/a | |
| R42 | Code runs on Windows and macOS and can be opened in Eclipse | Maven project, Java 17 baseline, `Launcher` | Yes (designed for it) | **macOS only. Windows and Eclipse not tried.** | M-41, M-42 | |
| R43 | Viva: code demonstrates conditions, loops, arrays and data manipulation | Conditions: `ProductValidator`, `ProductSearch.matches`, `StockLevel.fromQuantity`. Loops, arrays and data manipulation: `ProductSearch`, `Categories`, `ProductMapper` | Yes | n/a | M-43 | |

## Known limitations (be upfront about these in your report)

1. **Searching is done in memory.** `ProductService.search` loads all products and filters them in Java (`ProductSearch`). This is simple, easy to test and perfect for a catalogue of hundreds of products; for hundreds of thousands you would move the filters into MongoDB queries (the indexes are already in place).
2. **Delete is a hard delete.** Once Orders exist, deleting a product that appears in old orders would leave dangling references. Deactivating is the safe option (see design notes section 6).
3. **No user login / roles.** The brief's Products component does not require one (login belongs to other components).
4. **Stock is a single number on the product**, as the brief says. It is not yet linked to warehouse stock.
5. **Two people editing the same product at once**: the last save wins (no version check).
6. **Images are limited** to 6 per product, 5 MB each, PNG/JPG/GIF/BMP.
7. **Currency** is shown as GBP (`£`) only.
