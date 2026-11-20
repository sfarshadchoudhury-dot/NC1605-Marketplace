package com.marketplace.seeder;

import org.bson.Document;
import org.bson.json.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads seed-data/&lt;collection&gt;.json files. Each file is a JSON array of documents written in
 * MongoDB Extended JSON, so money is {"$numberDecimal": "9.99"} and dates are {"$date": "2026-10-01T09:00:00Z"}.
 */
public final class SeedFiles {

    /** The documents of every collection that has a file, keyed by collection name, in loading order. */
    private final Map<String, List<Document>> documents = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    private SeedFiles() {
    }

    public static SeedFiles load(Path folder) {
        SeedFiles files = new SeedFiles();
        for (CollectionRules.Rule rule : CollectionRules.ALL) {
            Path file = folder.resolve(rule.collection() + ".json");
            if (Files.exists(file)) {
                files.readFile(rule.collection(), file);
            } else {
                files.notes.add(rule.collection() + ".json not found: nothing to load for " + rule.owner() + " yet.");
            }
        }
        files.noteUnknownFiles(folder);
        return files;
    }

    private void readFile(String collection, Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            errors.add(file.getFileName() + ": cannot read the file (" + e.getMessage() + ")");
            return;
        }
        if (text.isEmpty()) {
            documents.put(collection, new ArrayList<>());
            return;
        }
        if (!text.startsWith("[")) {
            errors.add(file.getFileName() + ": must be a JSON array, starting with [ and ending with ]");
            return;
        }

        Object parsed;
        try {
            // Document.parse needs an object at the top, so wrap the array in one.
            parsed = Document.parse("{\"documents\": " + text + "}").get("documents");
        } catch (JsonParseException e) {
            errors.add(file.getFileName() + ": is not valid JSON (" + e.getMessage() + "). "
                    + "Look for a missing comma, quote or bracket near that point.");
            return;
        }

        List<Document> list = new ArrayList<>();
        if (parsed instanceof List<?> items) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof Document document) {
                    list.add(document);
                } else {
                    errors.add(file.getFileName() + ": item " + (i + 1) + " is not a { ... } document");
                }
            }
        }
        documents.put(collection, list);
    }

    /** Mentions any extra .json files, so a misspelt name such as "product.json" does not go unnoticed. */
    private void noteUnknownFiles(Path folder) {
        try (Stream<Path> stream = Files.list(folder)) {
            stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .filter(name -> CollectionRules.forCollection(name.substring(0, name.length() - 5)) == null)
                    .sorted()
                    .forEach(name -> notes.add(name + " is not a collection in SHARED-CONVENTIONS.md, so it is ignored."));
        } catch (IOException ignored) {
            // the folder was readable a moment ago; nothing useful to add
        }
    }

    public Map<String, List<Document>> documents() {
        return documents;
    }

    public List<Document> documentsOf(String collection) {
        return documents.getOrDefault(collection, List.of());
    }

    public List<String> errors() {
        return errors;
    }

    public List<String> notes() {
        return notes;
    }
}
