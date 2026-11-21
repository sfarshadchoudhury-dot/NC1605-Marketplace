package com.marketplace.seeder;

import com.marketplace.seeder.CollectionRules.Field;
import com.marketplace.seeder.CollectionRules.Link;
import com.marketplace.seeder.CollectionRules.Rule;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks the seed files against docs/SHARED-CONVENTIONS.md before anything is written to MongoDB.
 * Errors stop the seeder; warnings are printed but do not stop it.
 */
public final class SeedChecker {

    /** Required text fields that may still be "" (empty). */
    private static final Set<String> MAY_BE_EMPTY = Set.of("description", "comment", "line2");

    private final SeedFiles files;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** Every _id in every file, per collection, so links can be checked. */
    private final Map<String, Set<String>> idsByCollection = new HashMap<>();

    private SeedChecker(SeedFiles files) {
        this.files = files;
    }

    public static SeedChecker check(SeedFiles files) {
        SeedChecker checker = new SeedChecker(files);
        checker.run();
        return checker;
    }

    public List<String> errors() {
        return errors;
    }

    public List<String> warnings() {
        return warnings;
    }

    private void run() {
        errors.addAll(files.errors());
        collectIds();
        for (Map.Entry<String, List<Document>> entry : files.documents().entrySet()) {
            Rule rule = CollectionRules.forCollection(entry.getKey());
            for (Document document : entry.getValue()) {
                checkDocument(rule, document);
            }
        }
        checkOrders();
        checkCustomers();
        checkInventory();
        checkReviews();
        checkTransactions();
    }

    // ------------------------------------------------------------------ IDs

    private void collectIds() {
        for (Map.Entry<String, List<Document>> entry : files.documents().entrySet()) {
            Rule rule = CollectionRules.forCollection(entry.getKey());
            Set<String> ids = new HashSet<>();
            int position = 0;
            for (Document document : entry.getValue()) {
                position++;
                Object id = document.get("_id");
                String where = rule.collection() + ".json, item " + position;
                if (!(id instanceof String text)) {
                    errors.add(where + ": \"_id\" is missing or is not text. Use the format " + rule.idExample());
                    continue;
                }
                if (!rule.idPattern().matcher(text).matches()) {
                    errors.add(where + ": _id \"" + text + "\" has the wrong format. Use the format " + rule.idExample());
                }
                if (!ids.add(text)) {
                    errors.add(where + ": _id \"" + text + "\" is used more than once");
                }
            }
            idsByCollection.put(rule.collection(), ids);
        }
    }

    // ------------------------------------------------------------------ one document

    private void checkDocument(Rule rule, Document document) {
        String where = location(rule.collection(), document);
        checkFields(where, rule.fields(), document);

        for (String key : document.keySet()) {
            if (rule.field(key) == null) {
                warnings.add(where + ": field \"" + key + "\" is not in SHARED-CONVENTIONS.md (check the spelling, "
                        + "or add it to the conventions in a pull request)");
            }
        }

        for (Map.Entry<String, Set<String>> allowed : rule.allowedValues().entrySet()) {
            Object value = document.get(allowed.getKey());
            if (value instanceof String text && !allowed.getValue().contains(text)) {
                errors.add(where + ": " + allowed.getKey() + " \"" + text + "\" is not allowed. Use one of "
                        + sorted(allowed.getValue()));
            }
        }

        for (Link link : rule.links()) {
            Object value = document.get(link.field());
            if (value instanceof String id) {
                checkLink(where, link.field(), id, link.targetCollection());
            }
        }
    }

    private void checkFields(String where, List<Field> fields, Document document) {
        for (Field field : fields) {
            Object value = document.get(field.name());
            if (value == null) {
                if (field.required()) {
                    errors.add(where + ": required field \"" + field.name() + "\" is missing or null");
                }
                continue;
            }
            checkValue(where, field, value);
        }
    }

    private void checkValue(String where, Field field, Object value) {
        String name = field.name();
        switch (field.kind()) {
            case STRING -> {
                if (!(value instanceof String text)) {
                    errors.add(where + ": " + name + " must be text in \"quotes\"");
                } else if (field.required() && text.isBlank() && !MAY_BE_EMPTY.contains(name)) {
                    errors.add(where + ": " + name + " must not be empty");
                }
            }
            case INT -> {
                if (!(value instanceof Integer number)) {
                    errors.add(where + ": " + name + " must be a whole number such as 5 (no quotes, no decimal point)");
                } else if (number < 0) {
                    errors.add(where + ": " + name + " must not be negative");
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    errors.add(where + ": " + name + " must be true or false (no quotes)");
                }
            }
            case MONEY -> checkMoney(where, name, value);
            case DATE -> {
                if (!(value instanceof Date)) {
                    errors.add(where + ": " + name + " must be a date written as {\"$date\": \"2026-10-01T09:00:00Z\"}");
                }
            }
            case STRING_LIST -> {
                if (!(value instanceof List<?> list) || !list.stream().allMatch(item -> item instanceof String)) {
                    errors.add(where + ": " + name + " must be a list of text, for example [\"Books\", \"Office\"]");
                }
            }
            case ADDRESS -> {
                if (value instanceof Document address) {
                    checkFields(where + " > " + name, CollectionRules.ADDRESS_FIELDS, address);
                } else {
                    errors.add(where + ": " + name + " must be an address { line1, line2, city, postcode, country }");
                }
            }
            case ORDER_LINES -> checkOrderLines(where, value);
        }
    }

    private void checkMoney(String where, String name, Object value) {
        if (!(value instanceof Decimal128 decimal)) {
            errors.add(where + ": " + name + " must be money written as {\"$numberDecimal\": \"9.99\"}, not a plain number");
            return;
        }
        BigDecimal amount = decimal.bigDecimalValue();
        if (amount.signum() < 0) {
            errors.add(where + ": " + name + " must not be negative");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            errors.add(where + ": " + name + " has more than 2 decimal places (" + amount.toPlainString() + ")");
        }
    }

    private void checkOrderLines(String where, Object value) {
        if (!(value instanceof List<?> lines) || lines.isEmpty()) {
            errors.add(where + ": lines must be a list with at least one order line");
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String lineWhere = where + " > line " + (i + 1);
            if (!(lines.get(i) instanceof Document line)) {
                errors.add(lineWhere + ": must be a { ... } document");
                continue;
            }
            checkFields(lineWhere, CollectionRules.ORDER_LINE_FIELDS, line);
            if (line.get("productId") instanceof String productId) {
                checkLink(lineWhere, "productId", productId, "products");
            }
            if (line.get("quantity") instanceof Integer quantity && quantity < 1) {
                errors.add(lineWhere + ": quantity must be at least 1");
            }
            BigDecimal unitPrice = money(line.get("unitPrice"));
            BigDecimal lineTotal = money(line.get("lineTotal"));
            if (unitPrice != null && lineTotal != null && line.get("quantity") instanceof Integer quantity
                    && unitPrice.multiply(BigDecimal.valueOf(quantity)).compareTo(lineTotal) != 0) {
                errors.add(lineWhere + ": lineTotal " + lineTotal.toPlainString() + " should be unitPrice x quantity = "
                        + unitPrice.multiply(BigDecimal.valueOf(quantity)).toPlainString());
            }
        }
    }

    private void checkLink(String where, String field, String id, String targetCollection) {
        if (!files.documents().containsKey(targetCollection)) {
            errors.add(where + ": " + field + " \"" + id + "\" cannot be checked because " + targetCollection
                    + ".json is missing");
        } else if (!idsByCollection.getOrDefault(targetCollection, Set.of()).contains(id)) {
            errors.add(where + ": " + field + " \"" + id + "\" does not exist in " + targetCollection + ".json");
        }
    }

    // ------------------------------------------------------------------ rules across documents

    private void checkOrders() {
        for (Document order : files.documentsOf("orders")) {
            BigDecimal total = money(order.get("totalAmount"));
            if (total == null || !(order.get("lines") instanceof List<?> lines)) {
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            boolean complete = true;
            for (Object item : lines) {
                BigDecimal lineTotal = item instanceof Document line ? money(line.get("lineTotal")) : null;
                if (lineTotal == null) {
                    complete = false; // already reported as a field error
                } else {
                    sum = sum.add(lineTotal);
                }
            }
            if (complete && sum.compareTo(total) != 0) {
                errors.add(location("orders", order) + ": totalAmount " + total.toPlainString()
                        + " should be the sum of the line totals = " + sum.toPlainString());
            }
        }
    }

    private void checkCustomers() {
        Set<String> emails = new HashSet<>();
        for (Document customer : files.documentsOf("customers")) {
            if (!(customer.get("email") instanceof String email)) {
                continue;
            }
            String where = location("customers", customer);
            if (!email.equals(email.toLowerCase(Locale.ROOT)) || !email.contains("@")) {
                errors.add(where + ": email \"" + email + "\" must be a lower-case email address");
            }
            if (!emails.add(email.toLowerCase(Locale.ROOT))) {
                errors.add(where + ": email \"" + email + "\" is used by another customer");
            }
        }
    }

    private void checkInventory() {
        Set<String> pairs = new HashSet<>();
        Map<String, Integer> totals = new HashMap<>();
        for (Document stock : files.documentsOf("inventory")) {
            Object productId = stock.get("productId");
            Object warehouseId = stock.get("warehouseId");
            if (!pairs.add(productId + "|" + warehouseId)) {
                errors.add(location("inventory", stock) + ": there is already a record for " + productId
                        + " in " + warehouseId + " (one record per product per warehouse)");
            }
            if (productId instanceof String id && stock.get("quantity") instanceof Integer quantity) {
                totals.merge(id, quantity, Integer::sum);
            }
        }
        // Decision D1: products.stockQuantity must equal the total in inventory.
        for (Document product : files.documentsOf("products")) {
            Object id = product.get("_id");
            if (totals.containsKey(id) && product.get("stockQuantity") instanceof Integer stock
                    && !totals.get(id).equals(stock)) {
                warnings.add(location("products", product) + ": stockQuantity is " + stock
                        + " but inventory.json holds " + totals.get(id) + " in total (decision D1: they must match)");
            }
        }
    }

    private void checkReviews() {
        Map<String, Document> orders = byId(files.documentsOf("orders"));
        for (Document review : files.documentsOf("reviews")) {
            String where = location("reviews", review);
            if (review.get("rating") instanceof Integer rating && (rating < 1 || rating > 5)) {
                errors.add(where + ": rating must be from 1 to 5");
            }
            Document order = review.get("orderId") instanceof String orderId ? orders.get(orderId) : null;
            if (order == null) {
                continue;
            }
            if (!order.get("customerId", "").equals(review.get("customerId"))) {
                errors.add(where + ": orderId " + order.get("_id") + " belongs to a different customer");
            }
            if (!"DELIVERED".equals(order.get("status"))) {
                errors.add(where + ": orderId " + order.get("_id") + " is not DELIVERED, so it cannot be reviewed yet");
            }
            if (!orderContainsProduct(order, review.get("productId"))) {
                errors.add(where + ": orderId " + order.get("_id") + " does not contain " + review.get("productId"));
            }
        }
    }

    private void checkTransactions() {
        Map<String, Document> orders = byId(files.documentsOf("orders"));
        for (Document transaction : files.documentsOf("transactions")) {
            Document order = transaction.get("orderId") instanceof String orderId ? orders.get(orderId) : null;
            if (order != null && !order.get("customerId", "").equals(transaction.get("customerId"))) {
                errors.add(location("transactions", transaction) + ": customerId must be the order's customer ("
                        + order.get("customerId") + ")");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean orderContainsProduct(Document order, Object productId) {
        if (order.get("lines") instanceof List<?> lines) {
            for (Object item : lines) {
                if (item instanceof Document line && line.get("productId", "").equals(productId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Document> byId(List<Document> documents) {
        Map<String, Document> map = new HashMap<>();
        for (Document document : documents) {
            if (document.get("_id") instanceof String id) {
                map.put(id, document);
            }
        }
        return map;
    }

    private static BigDecimal money(Object value) {
        return value instanceof Decimal128 decimal ? decimal.bigDecimalValue() : null;
    }

    private static String location(String collection, Document document) {
        return collection + ".json " + document.get("_id");
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        list.sort(null);
        return list;
    }
}
