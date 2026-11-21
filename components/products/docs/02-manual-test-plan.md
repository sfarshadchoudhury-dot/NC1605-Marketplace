# 2. Manual test plan

The *Actual result*, *Pass/Fail* and *Evidence* columns are filled in while testing, with one screenshot per test named after it (for example `M-07.png`).
A test is marked Pass only when the expected outcome is seen. If something fails, the actual behaviour is recorded and the requirement is either fixed or listed as Incomplete.

Requirement IDs (R1, R2, ...) refer to [01-requirements-checklist.md](01-requirements-checklist.md).

## Preparation

1. Start MongoDB Community Server (Windows: `net start MongoDB`, or it is already running as a service; macOS: `brew services start mongodb-community`). Check with `mongosh --quiet --eval "db.runCommand({ping:1})"`.
2. Prepare these files somewhere easy to find (for the image tests):
   * `lamp1.png`, `lamp2.jpg`, `lamp3.gif`: any three real pictures under 5 MB (screenshots are fine).
   * `notes.txt`: any text file.
   * `fake.png`: a text file renamed to end in `.png`.
   * `big.png`: a file over 5 MB. macOS: `mkfile 6m big.png`. Windows (cmd): `fsutil file createnew big.png 6291456`.
3. Tests M-01 and M-38 need an **empty** catalogue / a stopped database; the rest assume the seed data is loaded (`mvn compile exec:java@seed`).
4. Test environment (recorded once): operating system and version, JDK version (`java -version`), MongoDB version, date: ______________________

## A. Start-up, catalogue and seed

| ID | Req. | Steps | Expected outcome | Actual result | Pass/Fail | Evidence |
|---|---|---|---|---|---|---|
| M-01 | R1, R32, R36 | With MongoDB running and the database empty, start the app (`mvn javafx:run`). | A loading spinner appears briefly, then "No products yet" with a hint about Add / seed. The pill next to the title says "Connected · marketplace". The list subtitle says "Showing 0 of 0 products" and all four summary cards show 0. | | | |
| M-02 | R1, R30, R38 | Run `mvn compile exec:java@seed`, then press **Refresh** in the app. | Console: "Added 15 product(s), skipped 0". The table lists 15 products; every product shows its photo in the details panel, the same photos as on every other team member's computer. | | | |
| M-03 | R38 | Run the seed command a second time. Press Refresh. | Console: "Added 0 product(s), skipped 15". The table still has exactly 15 products (no duplicates). | | | |
| M-04 | R1, R11, R31 | Look at the table. | Each row shows a thumbnail, name with a one-line description, ID (`PRD-nnnnn`), categories, price in £, a coloured stock badge (green in stock, amber low, red out) and an Active/Inactive badge. Inactive rows are greyed. | | | |
| M-05 | R20 | Click the column headers PRICE, STOCK, PRODUCT, STATUS (click twice for descending). | Rows re-order each time (price numerically, e.g. £9.99 before £29.99, not as text). | | | |
| M-06 | R1, R10, R11 | Click "Mechanical Keyboard". Then click the 2nd and 3rd thumbnails in the details panel. | The panel shows a large picture, three thumbnails, name, ID, price, status and stock badges, category chips, description, created/updated times, and Edit / Deactivate / Delete buttons. Clicking a thumbnail changes the big picture and moves the highlight. | | | |

## B. Adding products and validation

| ID | Req. | Steps | Expected outcome | Actual result | Pass/Fail | Evidence |
|---|---|---|---|---|---|---|
| M-07 | R2, R8, R9, R16 | Click **+ Add product**. Name `Test Mug`, description `Blue mug`, price `4.5`, stock `12`, tick "Home & Kitchen" and "Gifts" (add it with *Add category*), add `lamp1.png` and `lamp2.jpg`. **Save product.** | Dialogue closes. Green status message: `Added "Test Mug" (PRD-000nn)`. The new row is selected; price shows £4.50; both categories and both images appear in the details panel. | | | |
| M-08 | R13 to R16 | **+ Add product**, press **Save product** without typing anything. | Messages under the fields: "Name is required.", "Price is required.", "Stock quantity is required.", "Select or add at least one category." Those fields have a red outline; the cursor moves to Name; nothing is saved. | | | |
| M-09 | R13 | Name: three spaces only. Fill the other fields correctly. Save. | "Name is required." Nothing saved. | | | |
| M-10 | R14 | Try prices `0`, `-5`, `abc`, `12.345`, `1e3`, `£5`. Save each time. | `0` and `-5`: "Price must be greater than zero." The others: "Enter a valid price such as 19.99 (digits only, at most 2 decimal places)." | | | |
| M-11 | R15 | Try stock `-1`, `2.5`, `abc`, `99999999999`. | `-1`: "Stock quantity cannot be negative." `2.5` and `abc`: "...must be a whole number...". `99999999999`: "...too large...". Also check `0` is **accepted**. | | | |
| M-12 | R8 | Fill everything correctly but tick no category. Save. | "Select or add at least one category." | | | |
| M-13 | R8 | In the new-category box type `   Garden   tools  ` and press Enter. Then type `garden TOOLS` and press Enter. | One tick-box appears, named `Garden tools` (spaces tidied) and is ticked. The second entry does not create a duplicate. A 31-character name shows an error. | | | |

## C. Images

| ID | Req. | Steps | Expected outcome | Actual result | Pass/Fail | Evidence |
|---|---|---|---|---|---|---|
| M-14 | R9, R10, R26 | In the form click **Add images...**, select `lamp1.png` and `lamp3.gif` together. | Both appear as thumbnails, the newest in the big preview; counter shows "2 of 6 images". A copy of each exists in `data/images/` with a random name. | | | |
| M-15 | R28 | Add images: choose `notes.txt` (switch the file dialogue to "All files" if needed). | Red message: `"notes.txt" is not a supported image type. Use PNG, JPG, GIF or BMP.` Nothing is added. | | | |
| M-16 | R28 | Add `fake.png` (a renamed text file). | Message: `"fake.png" is not a valid PNG, JPG, GIF or BMP image.` | | | |
| M-17 | R28 | Add `big.png` (over 5 MB). | Message: `"big.png" is larger than 5 MB.` | | | |
| M-18 | R9 | Add images until there are 6, then try one more (or choose 8 files at once on a fresh form). | The Add button disables at 6; extra files are reported ("limit of 6 images was reached") and ignored. | | | |
| M-19 | R12, R26 | Edit "Test Mug", click the **X** on one thumbnail, **Save**. | The picture disappears from the details panel. The file for it is gone from `data/images/`; the other one remains. | | | |
| M-20 | R26 | **+ Add product**, add one image, then press **Cancel**. Look in `data/images/`. | The just-copied file is not left behind (the folder has no new file). | | | |
| M-21 | R22, R26, R27 | In a terminal: `mongosh --quiet marketplace --eval "printjson(db.products.findOne({name:'Test Mug'}))"` | `price` is `Decimal128('4.50')`; `_id` is `PRD-000nn`; `stockQuantity` is a number; `imagePaths` are like `images/3f2a....png` with **no** drive letter, no `/Users/...`. | | | |
| M-22 | R29 | Close the app. In `data/images/` delete one image that a product uses. Start the app and select that product. | No crash. The missing picture shows "Image unavailable" (in the table thumbnail and details); other pictures are fine. | | | |

## D. Editing, deactivating and deleting

| ID | Req. | Steps | Expected outcome | Actual result | Pass/Fail | Evidence |
|---|---|---|---|---|---|---|
| M-23 | R3, R20 | Select "Test Mug" and click **Edit** (or double-click the row). Change name, description, price to `5.25`, stock to `3`, categories, untick Active. Save. | The title shows "Product ID: PRD-000nn (the ID never changes)". After saving, the same ID is shown with the new values; there is still only one "Test Mug" row; the stock badge turns amber ("Low stock"). | | | |
| M-24 | R16 | Edit a product, set price to `abc` and stock to `-3`, Save. | Field errors shown; nothing changes; reopening the product shows the old values. | | | |
| M-25 | R4 | Select an active product, click **Deactivate**, then **Activate**. | After Deactivate: grey row, "Inactive" badge, button now says "Activate", toast message `... is now inactive.` The product is still in the list and stock is unchanged. After Activate it is back to normal. | | | |
| M-26 | R5, R33 | Click **Delete**. A dialogue "Delete "Test Mug"?" appears. Click **Cancel**. | The dialogue text warns it cannot be undone and suggests Deactivate. Cancel is the highlighted default. After Cancel nothing is deleted. | | | |
| M-27 | R5 | Click **Delete** again and **Delete product**. | The row disappears, toast message `Deleted "Test Mug" (PRD-000nn).`, the Total products card drops by one, the product's image files are removed from `data/images/`, the details panel returns to "Select a product". | | | |

## E. Search and filters

| ID | Req. | Steps | Expected outcome | Actual result | Pass/Fail | Evidence |
|---|---|---|---|---|---|---|
| M-28 | R6 | Type `LAMP` in the search box. Then ` lamp ` with spaces. Then `zzz`. | After a short pause the list shows only names containing "lamp" in any case ("Ergonomic Desk Lamp"). Spaces around the text are ignored. `zzz` gives "No products match your filters" with a Reset button. | | | |
| M-29 | R7, R8 | Choose Category = `Office`. | Only products with Office among their categories (including ones with other categories too). | | | |
| M-30 | R7, R16 | Type `abc` in Min price. Then fix it and enter Min `20`, Max `50`. Then Min `60`, Max `10`. | `abc`: red box and message "Min price must be a number such as 25 or 25.50. The list is not updated until this is fixed." Min 20/Max 50: only products £20.00 to £50.00 inclusive (check £49.50 and £39.90 appear). Min 60/Max 10: message "Max price must not be lower than min price." | | | |
| M-31 | R7 | Availability = "Out of stock", then "In stock". | Out of stock: only products with 0 stock (red badges). In stock: only products with stock above 0. | | | |
| M-32 | R4, R7 | Status = "Inactive only", then "Active only". | Inactive only: the greyed products. Active only: none greyed. | | | |
| M-33 | R7 | Category `Office` + Status "Active only" + Max price `100`. Then add name `desk`. | Rows must satisfy **all** conditions at once (compare against the full list by eye). Adding the name narrows the list further. | | | |
| M-34 | R31 | Press **Reset filters**. | All filter boxes clear (drop-downs return to "All categories", "Any availability", "Active and inactive") and the full list returns. | | | |
| M-35 | R32 | Search for `zzz`, then click **Show all products** inside the empty message. | "No products match your filters" appears, then the full list returns. | | | |

## F. Persistence, errors and configuration

| ID | Req. | Steps | Expected outcome | Actual result | Pass/Fail | Evidence |
|---|---|---|---|---|---|---|
| M-36 | R17 | Add a product, close the app completely, start it again. | The product (with its price, stock, categories and pictures) is still there. | | | |
| M-37 | R32, R37 | With the app open, stop MongoDB (Windows: `net stop MongoDB`; macOS: `brew services stop mongodb-community`). Click **Refresh**. Then start it again (`net start MongoDB` / `brew services start mongodb-community`) and click **Retry connection**. | After a few seconds a red "Database problem" panel appears with "Cannot reach MongoDB..." and the pill next to the title says "Not connected". After Retry the list returns. | | | |
| M-38 | R37 | Stop MongoDB (`net stop MongoDB` / `brew services stop mongodb-community`), start the app. | Same red panel within about 3 seconds, with the start commands for Windows and macOS. No in-memory fallback data is shown. Add/Edit report "Not connected to MongoDB". | | | |
| M-39 | R35 | Stop the app. Start it with `MONGODB_DATABASE=marketplace_demo` (see the README for the shell syntax), or create `config/application.properties` with `mongodb.database=marketplace_demo`. | The pill next to the title shows `Connected · marketplace_demo` and the catalogue is empty (a different database). The environment variable wins over the file if both are set. | | | |
| M-40 | R34 | Start the app with MongoDB stopped. During the ~3 seconds before the error appears, try dragging the window and clicking Refresh. | The window keeps responding (it does not freeze or turn "Not Responding"). | | | |

## G. Platforms

| ID | Req. | Steps | Expected outcome | Actual result | Pass/Fail | Evidence |
|---|---|---|---|---|---|---|
| M-41 | R42 | **On Windows:** install JDK 17+, Maven (or Eclipse), MongoDB Community Server. Follow the README quick start; run `mvn test`, the seed command and the app. | Everything in the README works unchanged. Record any step that needed a change. | | | |
| M-42 | R42 | **Eclipse (macOS or Windows):** follow README section 8: import as Maven project, run `Launcher`, run `SeedMain`, run `src/test/java` as JUnit. | App starts from Eclipse; seed works; tests pass. | | | |

---

## Automated test index

The brief's testing table has the columns *Test Number, Component, Description, Corresponding Requirements, Pass/Fail, Correction*.
The tests run with `mvn test` (unit tests) and `mvn verify -Pintegration` (adds the MongoDB integration tests).
The *Tests* column is the number of test methods in each class.

| Test no. | Component | Test class | What it checks | Tests | Req. | Pass/Fail |
|---|---|---|---|---|---|---|
| A-01 | Products | `ProductValidatorTest` | Required name, whitespace-only names (spaces, tab, newline), name length; zero, negative and invalid prices (`abc`, `12.345`, `1e3`, `NaN`, `$5`...); negative, fractional, non-numeric and too-large stock; categories, description and image-count rules; whole-form field-level errors; search-bar price range | 80 | R13 to R16, R7 | |
| A-02 | Products | `ProductSearchTest` | Name search (case, spaces); category (any of several); inclusive price range with different decimal scales; availability; status; **combined** filters; list not modified; stock-level counts; category collection | 18 | R6, R7, R8 | |
| A-03 | Products | `ProductServiceTest` | Add (cleaned values, unique IDs, rejected form uses no ID); **edit keeps ID** (also after 5 edits); invalid edit leaves product unchanged; **deactivate / reactivate**; delete removes image files; unknown IDs; image removed on edit; sorted search results | 19 | R2 to R5, R12, R16 | |
| A-04 | Products | `ImageStorageServiceTest` | PNG/JPG/GIF/BMP accepted and copied; relative portable reference; original not needed afterwards; **wrong extension, renamed text file, empty, > 5 MB rejected**; path-traversal refused; missing image handled; delete one file | 28 | R9, R26 to R29 | |
| A-05 | Products | `ProductMapperTest` | **Decimal128** price, `_id` is the product ID, round trip keeps every field, exact money arithmetic, defaults for missing fields | 7 | R17, R22 | |
| A-06 | Products | `AppConfigTest` | Environment overrides file overrides defaults; blank values ignored; password hidden when URI shown; example config has no credentials | 6 | R35 | |
| A-07 | Products | `SampleDataSeederTest` | 15 samples, **re-run adds nothing**, existing records never overwritten, a deleted sample is restored, sample data varied, every sample has its photo and every image file exists | 6 | R30, R38 | |
| A-08 | Products | `AppContextTest` | Unreachable MongoDB gives "Cannot reach MongoDB ... MongoDB Community Server ..."; malformed URI gives a clear error | 2 | R37 | |
| A-09 | Products | `FxmlLoadTest` | `main-view.fxml` and `product-form.fxml` load and every `@FXML` field is wired. **Skipped if no graphical session.** | 3 | R31 | |
| I-01 | Products | `MongoProductRepositoryIT` (needs MongoDB) | Stored format (`Decimal128`, string `_id`), indexes, **data survives restart**, sequential IDs, 40 concurrent ID requests all distinct, edit keeps document ID, unknown-ID update/delete, search on real data, **seed twice creates no duplicates** | 11 | R2, R3, R17, R22, R23, R38 | |

Evidence: a screenshot of the Maven summary (`Tests run: 169` for the unit tests, `Tests run: 11` for the integration run) and of the IDE JUnit view.
