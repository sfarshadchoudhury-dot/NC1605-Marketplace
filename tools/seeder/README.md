# Shared Seeder

Checks `seed-data/*.json` against [docs/SHARED-CONVENTIONS.md](../../docs/SHARED-CONVENTIONS.md) and loads it into your local MongoDB database `marketplace`.

```bash
cd tools/seeder
mvn -q compile exec:java                               # add missing seed records (safe to repeat)
mvn -q compile exec:java -Dexec.args="--check"         # only check the files, no MongoDB needed
mvn -q compile exec:java -Dexec.args="--reset"         # delete the seeded collections, then load them fresh
```

Full guide (rules, output, how to change your component's data): **[docs/SEED-DATA-GUIDE.md](../../docs/SEED-DATA-GUIDE.md)**.

| Class | Job |
|---|---|
| `SeederMain` | Reads the options, runs check then load, prints the results |
| `SeedFiles` | Reads each `seed-data/<collection>.json` (MongoDB Extended JSON) |
| `CollectionRules` | SHARED-CONVENTIONS section 4 written as data: ID formats, fields, types, allowed values, links. **Update it whenever the conventions change.** |
| `SeedChecker` | Finds every rule break (wrong ID, missing field, broken link, wrong total...) before anything is written |
| `MongoSeeder` | Adds missing documents, moves the ID counters past the seed IDs, creates the shared indexes |
