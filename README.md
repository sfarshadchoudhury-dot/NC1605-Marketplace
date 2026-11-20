# NC1605 Marketplace

> **Academic coursework notice:** this repository is part of the **NC1605 group coursework** for **Computer Science (Year 1) at Brunel University of London** and is submitted for assessment. It is public only so the team can collaborate and the work can be reviewed. Do not copy, reuse or submit any of this code or documentation as your own work.

Group coursework for **NC1605**: a desktop e-commerce marketplace built as seven components that share one MongoDB database.

**Stack (everyone):** Java 17+ · JavaFX 21 (FXML + CSS) · MongoDB · Maven · JUnit 5

---

## Team and components

| # | Component | Owner | Student ID | Branch | Status |
|---|---|---|---|---|---|
| 1 | Products (catalogue: add/edit/search, categories, images) | Farshad | 2408546 | `farshad-2408546-products` | In progress |
| 2 | Customers | _TBC_ | _TBC_ | _TBC_ | Not started |
| 3 | Orders | _TBC_ | _TBC_ | _TBC_ | Not started |
| 4 | Delivery & Logistics | _TBC_ | _TBC_ | _TBC_ | Not started |
| 5 | Inventory / Warehousing | _TBC_ | _TBC_ | _TBC_ | Not started |
| 6 | Reviews & Ratings | _TBC_ | _TBC_ | _TBC_ | Not started |
| 7 | Finance & Reporting | _TBC_ | _TBC_ | _TBC_ | Not started |

When you claim a component, update your own row in this table (see the [Team Guide](docs/TEAM-GUIDE.md), step 4).

---

## Designs (Figma)

UI designs and click-through prototypes for each component. Anyone with the link can view them.

| Component | Owner | Interactive prototype | Design file |
|---|---|---|---|
| Products | Farshad (2408546) | [Click-through demo](https://www.figma.com/proto/uJmMPHItOGvXNZSgwFZb2k/NC1605-Products-UI---Farshad-2408546?node-id=1-845&starting-point-node-id=1-845&scaling=contain) | [Figma file](https://www.figma.com/design/uJmMPHItOGvXNZSgwFZb2k/NC1605-Products-UI---Farshad-2408546) |
| Customers | _TBC_ | | |
| Orders | _TBC_ | | |
| Delivery & Logistics | _TBC_ | | |
| Inventory / Warehousing | _TBC_ | | |
| Reviews & Ratings | _TBC_ | | |
| Finance & Reporting | _TBC_ | | |

When your design is ready, add your links to your row (set the Figma file to “Anyone with the link can view” first).

---

## Read these first

| Document | What it is for |
|---|---|
| [CONTRIBUTING.md](CONTRIBUTING.md) | **Team rules**: branches, commits, pull requests, what must never be committed |
| [docs/TEAM-GUIDE.md](docs/TEAM-GUIDE.md) | Step-by-step guide: install the tools, clone, create your branch, add your project, push, open a pull request |
| [docs/SHARED-CONVENTIONS.md](docs/SHARED-CONVENTIONS.md) | **FINAL** data structure: every collection, field, ID format and status value. Everyone follows it exactly |
| [docs/SEED-DATA-GUIDE.md](docs/SEED-DATA-GUIDE.md) | Shared sample data: how to load it, check it and add your component's records |

---

## Repository layout

```
NC1605-Marketplace/
├── README.md                    this file
├── CONTRIBUTING.md              team rules
├── docs/
│   ├── TEAM-GUIDE.md
│   ├── SHARED-CONVENTIONS.md      FINAL data structure every component follows
│   └── SEED-DATA-GUIDE.md         how to load, check and change the shared sample data
├── seed-data/                   shared sample data, one JSON file per collection
├── tools/
│   └── seeder/                  checks seed-data/ and loads it into your local MongoDB
└── components/
    ├── products/                Farshad (2408546)
    ├── customers/
    ├── orders/
    ├── delivery/
    ├── inventory/
    ├── reviews/
    └── finance/
```

Each component is its **own Maven project** inside `components/<name>/`, so each one can be imported into Eclipse, run, tested and exported for submission on its own.

---

## Shared MongoDB

**Decision (final): every team member uses MongoDB Community Server, installed locally.** No Docker and no Atlas.

1. Install **MongoDB Community Server 8.0 or newer** from https://www.mongodb.com/try/download/community
   * **Windows:** run the MSI installer, choose *Complete*, keep **"Install MongoD as a Service"** ticked. It then starts by itself with Windows.
   * **macOS:** `brew tap mongodb/brew && brew install mongodb-community` then `brew services start mongodb-community`
2. Install **MongoDB Shell (`mongosh`)** from https://www.mongodb.com/try/download/shell (and optionally MongoDB Compass to look at the data).
3. Check it is running: `mongosh --quiet --eval "db.runCommand({ping:1})"` must print `{ ok: 1 }`.

| | Windows (Command Prompt as administrator) | macOS |
|---|---|---|
| Start | `net start MongoDB` | `brew services start mongodb-community` |
| Stop | `net stop MongoDB` | `brew services stop mongodb-community` |

MongoDB then listens on `localhost:27017` with no password (local development only), which is the default every component uses.

All components use database **`marketplace`** with one or more collections each. See [SHARED-CONVENTIONS.md](docs/SHARED-CONVENTIONS.md#3-mongodb).

### Shared sample data

So that everyone tests with the same records, load the shared seed data (first time, and whenever `seed-data/` changes; safe to repeat):

```bash
cd tools/seeder
mvn -q compile exec:java
```

Details: [SEED-DATA-GUIDE.md](docs/SEED-DATA-GUIDE.md).
