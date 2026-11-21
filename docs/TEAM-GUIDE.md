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

## Step 7a. Load the shared sample data (the common seeder)

Everyone tests with **exactly the same data** on their own laptop: 15 products **with their photos**, 8 customers, 10 orders, plus stock, shipments, payments and reviews (96 records). The IDs are fixed and every link matches, so for example `ORD-00004` belongs to `CUS-00004`, ships as `SHP-00004` and is paid by `TXN-00004` on every machine.
The seed files (`seed-data/`) and the product photos are in the repository, so you do not download anything yourself.

**Do this once, after Step 7 (MongoDB must be running):**

```bash
# 1. Get the latest code
git checkout staging
git pull

# 2. Go to the seeder
cd tools/seeder

# 3. Load everything
mvn -q compile exec:java
```

You see a table with an `added` count for each collection, then `Product photos: 15 copied`. Running it again is safe: it only adds what is missing and never changes your own records.

**In Eclipse instead of Maven:** File > Import > Maven > Existing Maven Projects > `tools/seeder`, then right-click `SeederMain.java` > **Run As > Java Application**.

**Check it worked:**

```bash
mongosh marketplace --quiet --eval "db.products.countDocuments()"      # must print 15 (or more if you added your own)
```

To see the products with their photos, start the Products app: `cd components/products` then `mvn javafx:run` (or run `Launcher` in Eclipse).

**Already have old test data, or products without photos?** Start fresh. This deletes the seeded collections in **your own** local database only (nobody else's) and asks you to type `yes`:

```bash
mvn -q compile exec:java -Dexec.args="--reset"
# Windows PowerShell:  mvn -q compile exec:java "-Dexec.args=--reset"
```

The photos are copied into `components/products/data/images/` on your computer. That folder is git-ignored: never commit it.
How to check or change the seed files, and every option: [SEED-DATA-GUIDE.md](SEED-DATA-GUIDE.md).

| Message | Fix |
|---|---|
| `ERROR: cannot use MongoDB` | Start MongoDB (Step 7), then run the seeder again |
| `different` is not 0 for products, or no photos in the app | You seeded before the photos existed: run it once with `--reset` (above) |
| `mvn` is not recognised | Install Maven (Step 1) or use the Eclipse way above |
| `cannot find the seed-data folder` | Run it from `tools/seeder` inside the repository |

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
| Seed loaded but no product photos | See Step 7a: run the common seeder once with `--reset` |
| *JavaFX runtime components are missing* | Run the `Launcher` class, not the `Application` class |
| Eclipse: *release version 17 not supported* | Add a JDK 17+ in Installed JREs and select it for the project |
| Permission denied when pushing | Accept the repository invitation, and sign in with the GitHub account that was invited |
