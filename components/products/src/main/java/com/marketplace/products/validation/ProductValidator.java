package com.marketplace.products.validation;

import com.marketplace.products.model.Categories;
import com.marketplace.products.model.ProductForm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

/**
 * All the product rules in one place. Each validateXxx method returns an error message,
 * or null when the value is acceptable. validate(form) runs them all and collects the
 * messages by field name so the UI can show each message next to its field.
 */
public final class ProductValidator {

    public static final String FIELD_NAME = "name";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_PRICE = "price";
    public static final String FIELD_STOCK = "stock";
    public static final String FIELD_CATEGORIES = "categories";
    public static final String FIELD_IMAGES = "images";
    public static final String FIELD_MIN_PRICE = "minPrice";
    public static final String FIELD_MAX_PRICE = "maxPrice";

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_IMAGES = 6;

    // Up to 9 digits, optionally followed by a dot and 1-2 decimals. Rejects "1e3", "+5", "12.345", "1,000".
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d{1,9}(\\.\\d{1,2})?");
    private static final Pattern WHOLE_NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern NEGATIVE_WHOLE_NUMBER_PATTERN = Pattern.compile("-\\d+");

    private static final String INVALID_PRICE_MESSAGE =
            "Enter a valid price such as 19.99 (digits only, at most 2 decimal places).";

    private ProductValidator() {
    }

    /** Validates every field of the add/edit form. */
    public static ValidationResult validate(ProductForm form) {
        ValidationResult result = new ValidationResult();
        addIfError(result, FIELD_NAME, validateName(form.name()));
        addIfError(result, FIELD_DESCRIPTION, validateDescription(form.description()));
        addIfError(result, FIELD_PRICE, validatePrice(form.priceText()));
        addIfError(result, FIELD_STOCK, validateStock(form.stockText()));
        addIfError(result, FIELD_CATEGORIES, validateCategories(form.categories()));
        addIfError(result, FIELD_IMAGES, validateImageCount(form.imagePaths()));
        return result;
    }

    public static String validateName(String name) {
        if (name == null || name.isBlank()) {
            return "Name is required.";
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            return "Name must be " + MAX_NAME_LENGTH + " characters or fewer.";
        }
        return null;
    }

    public static String validateDescription(String description) {
        if (description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            return "Description must be " + MAX_DESCRIPTION_LENGTH + " characters or fewer.";
        }
        return null;
    }

    public static String validatePrice(String priceText) {
        String text = priceText == null ? "" : priceText.trim();
        if (text.isEmpty()) {
            return "Price is required.";
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException e) {
            return INVALID_PRICE_MESSAGE;
        }
        if (value.signum() <= 0) {
            return "Price must be greater than zero.";
        }
        if (!PRICE_PATTERN.matcher(text).matches()) {
            return INVALID_PRICE_MESSAGE;
        }
        return null;
    }

    public static String validateStock(String stockText) {
        String text = stockText == null ? "" : stockText.trim();
        if (text.isEmpty()) {
            return "Stock quantity is required.";
        }
        if (NEGATIVE_WHOLE_NUMBER_PATTERN.matcher(text).matches()) {
            return "Stock quantity cannot be negative.";
        }
        if (!WHOLE_NUMBER_PATTERN.matcher(text).matches()) {
            return "Stock quantity must be a whole number (no decimals or letters).";
        }
        try {
            Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return "Stock quantity is too large (maximum " + Integer.MAX_VALUE + ").";
        }
        return null;
    }

    public static String validateCategories(List<String> categories) {
        List<String> cleaned = Categories.cleanList(categories);
        if (cleaned.isEmpty()) {
            return "Select or add at least one category.";
        }
        for (String category : cleaned) {
            if (category.length() > Categories.MAX_NAME_LENGTH) {
                return "Category names must be " + Categories.MAX_NAME_LENGTH + " characters or fewer.";
            }
        }
        return null;
    }

    public static String validateImageCount(List<String> imagePaths) {
        if (imagePaths != null && imagePaths.size() > MAX_IMAGES) {
            return "A product can have at most " + MAX_IMAGES + " images.";
        }
        return null;
    }

    /**
     * Validates the optional min/max price boxes of the search bar.
     * A blank box means "no limit"; zero is allowed here (unlike a product price).
     */
    public static ValidationResult validatePriceRange(String minText, String maxText) {
        ValidationResult result = new ValidationResult();
        addIfError(result, FIELD_MIN_PRICE, validateFilterPrice(minText, "Min"));
        addIfError(result, FIELD_MAX_PRICE, validateFilterPrice(maxText, "Max"));
        if (result.isValid() && !isBlank(minText) && !isBlank(maxText)
                && toPrice(minText).compareTo(toPrice(maxText)) > 0) {
            result.addError(FIELD_MAX_PRICE, "Max price must not be lower than min price.");
        }
        return result;
    }

    private static String validateFilterPrice(String text, String label) {
        if (isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        try {
            if (new BigDecimal(trimmed).signum() < 0) {
                return label + " price cannot be negative.";
            }
        } catch (NumberFormatException e) {
            return label + " price must be a number such as 25 or 25.50.";
        }
        if (!PRICE_PATTERN.matcher(trimmed).matches()) {
            return label + " price must be a number such as 25 or 25.50.";
        }
        return null;
    }

    /** Converts already-validated price text to money with exactly 2 decimal places. */
    public static BigDecimal toPrice(String priceText) {
        return new BigDecimal(priceText.trim()).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Converts already-validated stock text to an int. */
    public static int toStock(String stockText) {
        return Integer.parseInt(stockText.trim());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static void addIfError(ValidationResult result, String field, String message) {
        if (message != null) {
            result.addError(field, message);
        }
    }
}
