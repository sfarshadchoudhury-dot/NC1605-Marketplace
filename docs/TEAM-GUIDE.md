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
| **Docker Desktop** (recommended) | docker.com | `docker --version` |
| or **MongoDB Community Server** | mongodb.com/try/download/community | `mongosh --version` |

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

From the repository root:

```bash
docker compose up -d
```

It listens on `localhost:27017` with no password (local development only). Use database `marketplace`.

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
| *Cannot reach MongoDB* | `docker compose up -d` from the repo root; check `docker compose ps` |
| *JavaFX runtime components are missing* | Run the `Launcher` class, not the `Application` class |
| Eclipse: *release version 17 not supported* | Add a JDK 17+ in Installed JREs and select it for the project |
| Permission denied when pushing | Accept the repository invitation, and sign in with the GitHub account that was invited |
