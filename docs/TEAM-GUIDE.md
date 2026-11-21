# Team Guide: from zero to your first pull request

Follow these steps in order. Commands are for a terminal (macOS Terminal, or Git Bash / PowerShell on Windows). Read [CONTRIBUTING.md](../CONTRIBUTING.md) as well.

---

## Step 1. Install the tools

| Tool | Get it | Check it works |
|---|---|---|
| **Git** | git-scm.com/downloads (Windows: includes Git Bash) | `git --version` |
| **JDK 17 or newer** | adoptium.net (Eclipse Temurin). A full JDK, not a JRE | `java -version` |
| **Maven 3.9+** | maven.apache.org (or use Eclipse's built-in Maven) | `mvn -v` |
| **Eclipse IDE for Java Developers** | eclipse.org/downloads | It includes Maven support (m2e) and Git support (EGit) |
| **MongoDB Community Server 8.0+** | mongodb.com/try/download/community (Windows: keep "Install MongoD as a Service" ticked; macOS: `brew install mongodb-community`) | Starts on `localhost:27017` |
| **MongoDB Shell (mongosh)** | mongodb.com/try/download/shell | `mongosh --version` |

Tell Git who you are (use the email of your GitHub account):

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## Step 2. Accept the invitation

The repository is private. You will get an email/GitHub notification inviting you to **NC1605-Marketplace**. Accept it, then sign in to GitHub on your machine (Git will ask the first time you push; use a personal access token or GitHub Desktop / Git Credential Manager if asked for a password).

## Step 3. Clone the repository

```bash
cd ~/dev                      # or C:\dev on Windows (keep the path short)
git clone https://github.com/sfarshadchoudhury-dot/NC1605-Marketplace.git
cd NC1605-Marketplace
```

## Step 4. Create your branch

Name: `firstname-studentid-component`, all lowercase.

```bash
git checkout main
git pull
git checkout -b alex-2401234-orders        # use YOUR name, ID and component
git push -u origin alex-2401234-orders
```

Then edit **your row** of the team table in `README.md` (name, student ID, branch, status), commit and push:

```bash
git add README.md
git commit -m "Add Alex to the team table"
git push
```

## Step 5. Create your Maven project in `components/<your-component>/`

Folder names: `products`, `customers`, `orders`, `delivery`, `inventory`, `reviews`, `finance`.

The quickest start is to copy the `pom.xml` from `components/products/` (once it is merged, or from the `farshad-2408546-products` branch) and change only these lines:

```xml
<groupId>com.marketplace</groupId>
<artifactId>orders-component</artifactId>      <!-- your component -->
<name>Orders Component</name>
```

and the main class names in the JavaFX / exec plugin sections. Keep the Java, JavaFX, MongoDB driver and JUnit versions the same (see CONTRIBUTING, section 7).

Use the package `com.marketplace.<component>`, for example:

```
components/orders/
├── pom.xml
├── config/application.properties.example
└── src/
    ├── main/java/com/marketplace/orders/...
    ├── main/resources/com/marketplace/orders/...
    └── test/java/com/marketplace/orders/...
```

Use the shared names from [SHARED-CONVENTIONS.md](SHARED-CONVENTIONS.md) for every ID and collection.

## Step 6. Open it in Eclipse

1. **File > Import... > Maven > Existing Maven Projects > Next**.
2. Browse to `NC1605-Marketplace/components/<your-component>`, tick its `pom.xml`, **Finish**.
3. Wait for the dependency download (bottom-right progress bar).
4. If you see red errors: right-click the project > **Maven > Update Project... > OK**.
5. Check **Window > Preferences > Java > Installed JREs** shows a JDK 17+.

JavaFX tip: start the app from a small `Launcher` class with a `main` method that calls your `Application` class (see `components/products/.../Launcher.java`). Starting the `Application` class directly gives *"JavaFX runtime components are missing"*.

## Step 7. Start MongoDB

We all use **MongoDB Community Server** installed on our own machine (decision final: no Docker, no Atlas).

```bash
# Windows: it runs as a service and starts with Windows. If it is stopped (Command Prompt as administrator):
net start MongoDB

# macOS (Homebrew)
brew services start mongodb-community

# Check (any system): must print { ok: 1 }
mongosh --quiet --eval "db.runCommand({ping:1})"
```

It listens on `localhost:27017` with no password (local development only). Use database `marketplace`.

## Step 7a. Load the sample products (seed)

Everyone should have the **same 15 sample products with the same photos** (PRD-00001 to PRD-00015), so screenshots, tests and links from your component (for example an order line with `productId: "PRD-00003"`) match on every computer.
The photos are stored in the repository (`components/products/src/main/resources/com/marketplace/products/sample-images/`), so you do not download anything yourself.

**Do this once, after Step 7 (MongoDB must be running):**

```bash
# 1. Get the latest code (the Products component and its photos)
git checkout staging
git pull

# 2. Go to the Products component (the folder with pom.xml)
cd components/products

# 3. Load the samples
mvn compile exec:java@seed
```

You should see `Added 15 product(s), skipped 0`. Running it again is safe: it says `skipped 15` and changes nothing.

**In Eclipse instead of Maven:** import `components/products` as an existing Maven project (Step 6), then right-click `src/main/java/com/marketplace/products/SeedMain.java` > **Run As > Java Application**.

**Check it worked:**

```bash
mongosh marketplace --quiet --eval "db.products.countDocuments()"      # must print 15
```

To see the products and their photos, start the Products app from the same folder: `mvn javafx:run` (or run `Launcher` in Eclipse).

**Seeded before the photos were added, or your IDs do not start at PRD-00001?** Reset the sample data once and seed again. This deletes **all** products in your own local database (nobody else's) and restarts the IDs at PRD-00001:

```bash
mongosh marketplace --eval "db.products.drop(); db.counters.deleteOne({_id: 'products'})"
mvn compile exec:java@seed
```

The photos are copied into `components/products/data/images/` on your computer. That folder is git-ignored: never commit it. Run the seed command from `components/products`, or the photos end up in a different `data` folder and the app shows "Image unavailable".

| Message | Fix |
|---|---|
| `SEED FAILED: Cannot reach MongoDB` | Start MongoDB (Step 7), then run the seed again |
| `Added 0 product(s), skipped 15` but you see no photos | You seeded before the photos existed: use the reset commands above |
| `mvn` is not recognised | Install Maven (Step 1) or use the Eclipse way above |
| `No POM in this directory` | You are not in `components/products`: run `cd components/products` first |

## Step 8. Daily routine

```bash
# 1. Start of the session: get everyone's merged work
git checkout main
git pull
git checkout alex-2401234-orders
git merge main                 # fix any conflicts, then commit

# 2. Work, then check what changed
git status

# 3. Save your work (small commits, clear messages)
git add components/orders
git commit -m "Add order total calculation"

# 4. Upload it
git push
```

Always `git add` your own folder, not `git add .` from the root, so you never commit something by accident.

## Step 9. Open a pull request

When a piece of work is finished and tested:

1. In your component folder: `mvn clean test` passes and `mvn javafx:run` starts the app.
2. Merge the latest `main` into your branch (step 8) and push.
3. On GitHub: **Pull requests > New pull request**, base **`main`**, compare **your branch**.
4. Fill in the template and ask one teammate in the group chat to review.
5. After approval, merge with **"Create a merge commit"**.

## Step 10. Exporting your component for submission

Done on your own machine, never committed:

1. In Eclipse: **File > Export > General > File System**, select your component's project.
2. Include `src`, `pom.xml`, `config`, docs and the Eclipse files (`.project`, `.classpath`, `.settings`).
3. Zip it as `<StudentID>_NC1605_Implementation_Code.zip`.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `git push` is rejected ("fetch first") | Someone pushed to your branch, or you pushed from another computer: `git pull`, then `git push` |
| Merge conflict after `git merge main` | Open the files Git lists, keep the right lines, delete the `<<<<<<<`, `=======`, `>>>>>>>` markers, then `git add` and `git commit` |
| You committed to `main` by mistake (not pushed yet) | `git branch my-branch-name` then `git reset --hard origin/main`, then `git checkout my-branch-name` |
| You committed a password | Tell the group straight away and change the password |
| *Cannot reach MongoDB* | Start MongoDB Community Server (Windows: `net start MongoDB`; macOS: `brew services start mongodb-community`), then check with `mongosh --eval "db.runCommand({ping:1})"` |
| Seed loaded but no product photos | See Step 7a: reset the sample data and seed again from `components/products` |
| *JavaFX runtime components are missing* | Run the `Launcher` class, not the `Application` class |
| Eclipse: *release version 17 not supported* | Add a JDK 17+ in Installed JREs and select it for the project |
| Permission denied when pushing | Accept the repository invitation, and sign in with the GitHub account that was invited |
