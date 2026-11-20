package com.marketplace.seeder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules from docs/SHARED-CONVENTIONS.md, section 4, written as data so the checker can use them.
 * If the conventions change, change this class in the same pull request.
 */
public final class CollectionRules {

    /** The kinds of value a field can hold (SHARED-CONVENTIONS section 2). */
    public enum Kind {
        STRING, INT, BOOLEAN, MONEY, DATE, STRING_LIST, ADDRESS, ORDER_LINES
    }

    /** One field of a collection. */
    public record Field(String name, Kind kind, boolean required) {
    }

    /** A field that must hold the _id of a document in another collection. */
    public record Link(String field, String targetCollection) {
    }

    /** Everything the checker needs to know about one collection. */
    public record Rule(String collection, String owner, Pattern idPattern, String idExample,
                       List<Field> fields, Map<String, Set<String>> allowedValues, List<Link> links) {

        public Field field(String name) {
            for (Field field : fields) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
            return null;
        }
    }

    /** Fields of the embedded Address document (customers, orders, warehouses). */
    public static final List<Field> ADDRESS_FIELDS = List.of(
            new Field("line1", Kind.STRING, true),
            new Field("line2", Kind.STRING, false),
            new Field("city", Kind.STRING, true),
            new Field("postcode", Kind.STRING, true),
            new Field("country", Kind.STRING, true));

    /** Fields of each embedded order line. */
    public static final List<Field> ORDER_LINE_FIELDS = List.of(
            new Field("productId", Kind.STRING, true),
            new Field("productName", Kind.STRING, true),
            new Field("unitPrice", Kind.MONEY, true),
            new Field("quantity", Kind.INT, true),
            new Field("lineTotal", Kind.MONEY, true));

    /**
     * All collections in the order they are loaded: a collection comes after the ones it points to,
     * so the output reads naturally (MongoDB itself does not mind the order).
     */
    public static final List<Rule> ALL = List.of(
            new Rule("warehouses", "Inventory / Warehousing", Pattern.compile("WHS-\\d{3}"), "WHS-001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("name", Kind.STRING, true),
                            new Field("address", Kind.ADDRESS, true),
                            new Field("active", Kind.BOOLEAN, true),
                            new Field("createdAt", Kind.DATE, true),
                            new Field("updatedAt", Kind.DATE, true)),
                    Map.of(),
                    List.of()),

            new Rule("products", "Products", Pattern.compile("PRD-\\d{5}"), "PRD-00001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("name", Kind.STRING, true),
                            new Field("description", Kind.STRING, true),
                            new Field("price", Kind.MONEY, true),
                            new Field("stockQuantity", Kind.INT, true),
                            new Field("active", Kind.BOOLEAN, true),
                            new Field("categories", Kind.STRING_LIST, true),
                            new Field("imagePaths", Kind.STRING_LIST, true),
                            new Field("createdAt", Kind.DATE, true),
                            new Field("updatedAt", Kind.DATE, true)),
                    Map.of(),
                    List.of()),

            new Rule("customers", "Customers", Pattern.compile("CUS-\\d{5}"), "CUS-00001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("firstName", Kind.STRING, true),
                            new Field("lastName", Kind.STRING, true),
                            new Field("email", Kind.STRING, true),
                            new Field("phone", Kind.STRING, false),
                            new Field("address", Kind.ADDRESS, true),
                            new Field("active", Kind.BOOLEAN, true),
                            new Field("passwordHash", Kind.STRING, false),
                            new Field("createdAt", Kind.DATE, true),
                            new Field("updatedAt", Kind.DATE, true)),
                    Map.of(),
                    List.of()),

            new Rule("inventory", "Inventory / Warehousing", Pattern.compile("STK-\\d{5}"), "STK-00001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("productId", Kind.STRING, true),
                            new Field("warehouseId", Kind.STRING, true),
                            new Field("quantity", Kind.INT, true),
                            new Field("minLevel", Kind.INT, true),
                            new Field("updatedAt", Kind.DATE, true)),
                    Map.of(),
                    List.of(new Link("productId", "products"),
                            new Link("warehouseId", "warehouses"))),

            new Rule("orders", "Orders", Pattern.compile("ORD-\\d{5}"), "ORD-00001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("customerId", Kind.STRING, true),
                            new Field("status", Kind.STRING, true),
                            new Field("lines", Kind.ORDER_LINES, true),
                            new Field("totalAmount", Kind.MONEY, true),
                            new Field("deliveryAddress", Kind.ADDRESS, true),
                            new Field("orderedAt", Kind.DATE, true),
                            new Field("updatedAt", Kind.DATE, true)),
                    Map.of("status", Set.of("PENDING", "PAID", "SHIPPED", "DELIVERED", "CANCELLED")),
                    List.of(new Link("customerId", "customers"))),

            new Rule("shipments", "Delivery & Logistics", Pattern.compile("SHP-\\d{5}"), "SHP-00001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("orderId", Kind.STRING, true),
                            new Field("warehouseId", Kind.STRING, true),
                            new Field("status", Kind.STRING, true),
                            new Field("courier", Kind.STRING, false),
                            new Field("trackingNumber", Kind.STRING, false),
                            new Field("dispatchedAt", Kind.DATE, false),
                            new Field("deliveredAt", Kind.DATE, false),
                            new Field("createdAt", Kind.DATE, true),
                            new Field("updatedAt", Kind.DATE, true)),
                    Map.of("status", Set.of("PREPARING", "DISPATCHED", "IN_TRANSIT", "DELIVERED", "FAILED")),
                    List.of(new Link("orderId", "orders"),
                            new Link("warehouseId", "warehouses"))),

            new Rule("transactions", "Finance & Reporting", Pattern.compile("TXN-\\d{5}"), "TXN-00001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("orderId", Kind.STRING, true),
                            new Field("customerId", Kind.STRING, true),
                            new Field("type", Kind.STRING, true),
                            new Field("amount", Kind.MONEY, true),
                            new Field("method", Kind.STRING, true),
                            new Field("status", Kind.STRING, true),
                            new Field("createdAt", Kind.DATE, true)),
                    Map.of("type", Set.of("PAYMENT", "REFUND"),
                            "method", Set.of("CARD", "PAYPAL", "BANK_TRANSFER"),
                            "status", Set.of("PENDING", "COMPLETED", "FAILED")),
                    List.of(new Link("orderId", "orders"),
                            new Link("customerId", "customers"))),

            new Rule("reviews", "Reviews & Ratings", Pattern.compile("REV-\\d{5}"), "REV-00001",
                    List.of(new Field("_id", Kind.STRING, true),
                            new Field("productId", Kind.STRING, true),
                            new Field("customerId", Kind.STRING, true),
                            new Field("orderId", Kind.STRING, false),
                            new Field("rating", Kind.INT, true),
                            new Field("title", Kind.STRING, true),
                            new Field("comment", Kind.STRING, true),
                            new Field("visible", Kind.BOOLEAN, true),
                            new Field("createdAt", Kind.DATE, true)),
                    Map.of(),
                    List.of(new Link("productId", "products"),
                            new Link("customerId", "customers"),
                            new Link("orderId", "orders"))));

    private CollectionRules() {
    }

    /** Returns the rule for a collection name, or null if there is none. */
    public static Rule forCollection(String collection) {
        for (Rule rule : ALL) {
            if (rule.collection().equals(collection)) {
                return rule;
            }
        }
        return null;
    }
}
