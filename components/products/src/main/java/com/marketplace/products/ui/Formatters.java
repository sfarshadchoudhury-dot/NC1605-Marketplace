package com.marketplace.products.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns values into display text. Only used on the JavaFX thread (NumberFormat is not thread-safe). */
public final class Formatters {

    private static final NumberFormat PRICE_FORMAT = NumberFormat.getCurrencyInstance(Locale.UK);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private Formatters() {
    }

    /** 39.9 becomes "£39.90". The BigDecimal is formatted exactly; it never goes through double. */
    public static String price(BigDecimal price) {
        return PRICE_FORMAT.format(price);
    }

    public static String dateTime(Instant instant) {
        return DATE_TIME_FORMAT.format(instant);
    }
}
