# Team Rules

These rules keep seven people working in one repository without overwriting each other's work.
If you disagree with a rule, raise it in the group chat. Do not just ignore it.

---

## 1. Branches

1. **Never push directly to `main`.** `main` holds only work that has been reviewed through a pull request.
2. Everyone works on **their own branch**, named:

   ```
   firstname-studentid-component
   ```

   Lowercase, hyphens only. Examples: `farshad-2408546-products`, `alex-2401234-orders`.
3. One person per branch. Do not commit to someone else's branch unless they ask you to.
4. If you need a short-lived extra branch (for example to try something), start it from your own branch and prefix it with your name: `farshad-2408546-products-search-fix`.

## 2. Folders: stay in your own

1. Your code goes **only** in `components/<your-component>/`.
2. Do **not** edit another component's folder. If you need a change there, ask the owner in the group chat or open an issue.
3. Shared files (`README.md`, `CONTRIBUTING.md`, `docs/`, `.gitignore`) are changed **only by pull request**, and the PR description must say what changed and why.
   The one exception: you may edit **your own row** of the team table in `README.md`.

## 3. Shared names and IDs

1. The names in [docs/SHARED-CONVENTIONS.md](docs/SHARED-CONVENTIONS.md) (`productId`, `customerId`, `orderId`, `warehouseId`, collection names, ID formats) must be used **exactly as written**: same spelling, same capital letters, same type.
2. The shared names are **final**. Only the repository owner (Farshad) changes `SHARED-CONVENTIONS.md`; if you need a new shared name, ask him in the group chat. Nobody renames a shared name on their own.
3. Never write into another component's MongoDB collection. You may **read** it. If you need something changed there, ask the owner to provide a method. The only exception is the stock update in `SHARED-CONVENTIONS.md` Q5.

## 4. Commits

1. Commit small and often, at least at the end of every working session.
2. Write messages in the imperative, saying what the commit does:

   ```
   Add product search by category
   Fix price validation for negative numbers
   Update order screen layout
   ```

   Not: `update`, `stuff`, `final`, `final2`, `asdf`.
3. Before committing, run `git status` and check you are only committing files you meant to.
4. Commit under your **own** name and account. Your commit history is evidence of your own work.

## 5. Never commit these

| Never commit | Why |
|---|---|
| Passwords, connection strings with a password, API keys | Anyone with access to the repo could use them. Use environment variables (`MONGODB_URI`). |
| `config/application.properties` | Personal local settings. Commit `application.properties.example` instead. |
| `target/`, `bin/`, `*.class`, `*.jar` | Build output. Maven rebuilds it. |
| `data/` and uploaded images | Local test data. |
| IntelliJ / VS Code files (`.idea/`, `*.iml`, `.vscode/`) | Personal editor settings. |
| Large files over 10 MB, zip files of the project | Bloats the repo. Submission zips are made on your own machine. |

The root `.gitignore` already blocks most of these. If you commit a password by mistake, **tell the group immediately** and change that password: deleting the file in a later commit does not remove it from history.

## 6. Pull requests (getting your work into `main`)

1. Before opening a PR, from your component folder:
   * `mvn clean test` passes.
   * The application starts (`mvn javafx:run`).
   * You have merged the latest `main` into your branch (see the Team Guide, step 8).
2. Open the PR **from your branch into `main`** and fill in the template.
3. **At least one other team member reviews it** before it is merged. Reviewers check: it only touches the owner's folder (or the change to shared files is explained), the shared names match `SHARED-CONVENTIONS.md`, and nothing from section 5 is included.
4. The **owner** merges their own PR after approval. Use **"Create a merge commit"** (not squash) so everyone's individual commits stay visible.
5. Do not merge a PR with failing tests.

## 7. Tools and versions

Everyone uses the same versions so the components fit together:

| Tool | Version |
|---|---|
| JDK | 17 or newer (`<maven.compiler.release>17</maven.compiler.release>` in every `pom.xml`) |
| JavaFX | 21.0.13 |
| MongoDB Java driver | `mongodb-driver-sync` 5.14.0 |
| JUnit | 5 (Jupiter) |
| Maven | 3.9 or newer |
| MongoDB server | MongoDB Community Server 8.0 or newer, installed locally on `localhost:27017` (no Docker, no Atlas) |
| IDE | Eclipse IDE for Java Developers (the brief asks for an Eclipse project) |

Package names: `com.marketplace.<component>` (for example `com.marketplace.orders`).
Maven coordinates: `groupId` `com.marketplace`, `artifactId` `<component>-component`.

## 8. Communication

1. Say in the group chat when you start a PR, and when you change anything shared.
2. If you are blocked by another component (you need their data or a method), ask early. Agree what you need and record the decision in `SHARED-CONVENTIONS.md`.
3. Keep decisions in writing (group chat or PR comments). They are evidence for the group work.

## 9. Academic integrity

Each member writes and must be able to explain **their own component**. Link to other components through the shared names and collections; do not copy another member's code into your folder. Follow the brief's academic-misconduct rules, including any declaration it requires.
