package com.marketplace.products.validation;

import com.marketplace.products.model.ProductForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductValidatorTest {

    // ---------- Name (requirements: required, non-blank) ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "     ", "\t", "\n", " \t \n "})
    void blankOrWhitespaceOnlyNameIsRejected(String name) {
        assertEquals("Enter a product name (up to 100 characters).", ProductValidator.validateName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Desk Lamp", "  Desk Lamp  ", "A", "USB-C Cable 2 m"})
    void ordinaryNamesAreAccepted(String name) {
        assertNull(ProductValidator.validateName(name));
    }

    @Test
    void nameLengthLimitIsEnforcedAfterTrimming() {
        assertNull(ProductValidator.validateName("x".repeat(ProductValidator.MAX_NAME_LENGTH)));
        assertNull(ProductValidator.validateName("  " + "x".repeat(ProductValidator.MAX_NAME_LENGTH) + "  "));
        assertNotNull(ProductValidator.validateName("x".repeat(ProductValidator.MAX_NAME_LENGTH + 1)));
    }

    // ---------- Price (requirements: required, positive number) ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingPriceIsRequired(String price) {
        assertEquals("Enter a price greater than £0.00.", ProductValidator.validatePrice(price));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "0.00", "-1", "-0.01", "-100.50"})
    void zeroAndNegativePricesAreRejected(String price) {
        assertEquals("Enter a price greater than £0.00.", ProductValidator.validatePrice(price));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12abc", "1e3", "12.345", "0.001", "1,000", "$5", "£5", "NaN", "Infinity",
            "+5", ".5", "5.", "1.2.3", "1 000", "1234567890"})
    void invalidPriceFormatsAreRejected(String price) {
        String message = ProductValidator.validatePrice(price);
        assertNotNull(message, "should have been rejected: " + price);
        assertTrue(message.startsWith("Enter a valid price"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "19.99", "19.9", "100", "999999999.99", " 12.50 "})
    void validPricesAreAccepted(String price) {
        assertNull(ProductValidator.validatePrice(price));
    }

    @Test
    void toPriceAlwaysGivesTwoDecimalPlacesWithoutFloatingPointError() {
        assertEquals("5.00", ProductValidator.toPrice("5").toPlainString());
        assertEquals("19.90", ProductValidator.toPrice("19.9").toPlainString());
        assertEquals("0.10", ProductValidator.toPrice(" 0.1 ").toPlainString());
        // 0.10 + 0.20 is exactly 0.30 with BigDecimal (with double it would be 0.30000000000000004)
        assertEquals(new BigDecimal("0.30"), ProductValidator.toPrice("0.10").add(ProductValidator.toPrice("0.20")));
    }

    // ---------- Stock (requirements: required, non-negative integer) ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingStockIsRequired(String stock) {
        assertEquals("Enter the stock as a whole number (0 or more).", ProductValidator.validateStock(stock));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-10", "-999"})
    void negativeStockIsRejected(String stock) {
        assertEquals("Enter the stock as a whole number (0 or more).", ProductValidator.validateStock(stock));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "1.0", "0.5", "abc", "1e2", "5 0", "ten", "1,000", "+3", "--1", "0x10"})
    void fractionalOrNonNumericStockIsRejected(String stock) {
        String message = ProductValidator.validateStock(stock);
        assertNotNull(message, "should have been rejected: " + stock);
        assertEquals("Enter the stock as a whole number (0 or more).", message);
    }

    @Test
    void stockLargerThanAnIntIsRejected() {
        String message = ProductValidator.validateStock("2147483648");
        assertNotNull(message);
        assertTrue(message.contains("too large"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "007", " 25 ", "2147483647"})
    void validStockIsAccepted(String stock) {
        assertNull(ProductValidator.validateStock(stock));
    }

    @Test
    void toStockParsesTrimmedText() {
        assertEquals(25, ProductValidator.toStock(" 25 "));
        assertEquals(0, ProductValidator.toStock("0"));
    }

    // ---------- Categories, description and images ----------

    @Test
    void atLeastOneCategoryIsRequired() {
        assertNotNull(ProductValidator.validateCategories(null));
        assertNotNull(ProductValidator.validateCategories(new ArrayList<>()));
        assertNotNull(ProductValidator.validateCategories(List.of("  ", "")));
        assertNull(ProductValidator.validateCategories(List.of("Books")));
        assertNull(ProductValidator.validateCategories(List.of("Books", "Office")));
        assertEquals("Choose at least one category.", ProductValidator.validateCategories(new ArrayList<>()));
    }

    @Test
    void overlongCategoryNameIsRejected() {
        assertNotNull(ProductValidator.validateCategories(List.of("c".repeat(31))));
        assertNull(ProductValidator.validateCategories(List.of("c".repeat(30))));
    }

    @Test
    void descriptionIsOptionalButLimited() {
        assertNull(ProductValidator.validateDescription(null));
        assertNull(ProductValidator.validateDescription(""));
        assertNull(ProductValidator.validateDescription("d".repeat(ProductValidator.MAX_DESCRIPTION_LENGTH)));
        assertNotNull(ProductValidator.validateDescription("d".repeat(ProductValidator.MAX_DESCRIPTION_LENGTH + 1)));
    }

    @Test
    void imageCountIsLimited() {
        List<String> six = List.of("images/1.png", "images/2.png", "images/3.png", "images/4.png", "images/5.png",
                "images/6.png");
        List<String> seven = new ArrayList<>(six);
        seven.add("images/7.png");
        assertNull(ProductValidator.validateImageCount(six));
        assertNotNull(ProductValidator.validateImageCount(seven));
        assertNull(ProductValidator.validateImageCount(new ArrayList<>()));
    }

    // ---------- Whole form: field-level errors ----------

    @Test
    void validFormHasNoErrors() {
        ProductForm form = new ProductForm("Lamp", "", "12.50", "3", true, List.of("Office"), new ArrayList<>());
        assertTrue(ProductValidator.validate(form).isValid());
    }

    @Test
    void everyBadFieldGetsItsOwnMessageUnderItsFieldName() {
        ProductForm form = new ProductForm("   ", "", "-5", "2.5", true, new ArrayList<>(), new ArrayList<>());
        ValidationResult result = ProductValidator.validate(form);

        assertFalse(result.isValid());
        assertTrue(result.hasError(ProductValidator.FIELD_NAME));
        assertTrue(result.hasError(ProductValidator.FIELD_PRICE));
        assertTrue(result.hasError(ProductValidator.FIELD_STOCK));
        assertTrue(result.hasError(ProductValidator.FIELD_CATEGORIES));
        assertFalse(result.hasError(ProductValidator.FIELD_DESCRIPTION));
        assertEquals(4, result.getErrors().size());
        assertEquals(ProductValidator.FIELD_NAME, result.getFirstErrorField(), "focus should go to the first field");
    }

    @Test
    void onlyTheBadFieldIsReported() {
        ProductForm form = new ProductForm("Lamp", "", "10", "-1", true, List.of("Office"), new ArrayList<>());
        ValidationResult result = ProductValidator.validate(form);
        assertEquals(1, result.getErrors().size());
        assertEquals("Enter the stock as a whole number (0 or more).", result.getError(ProductValidator.FIELD_STOCK));
    }

    // ---------- Search-bar price range ----------

    @Test
    void blankPriceRangeBoxesMeanNoLimit() {
        assertTrue(ProductValidator.validatePriceRange("", "").isValid());
        assertTrue(ProductValidator.validatePriceRange(null, "  ").isValid());
        assertTrue(ProductValidator.validatePriceRange("10", "").isValid());
        assertTrue(ProductValidator.validatePriceRange("", "99.99").isValid());
    }

    @Test
    void validPriceRangeIsAcceptedIncludingEqualAndZeroBounds() {
        assertTrue(ProductValidator.validatePriceRange("10", "20").isValid());
        assertTrue(ProductValidator.validatePriceRange("15", "15").isValid());
        assertTrue(ProductValidator.validatePriceRange("0", "0").isValid());
    }

    @Test
    void invalidPriceRangeBoxesAreReportedPerBox() {
        ValidationResult result = ProductValidator.validatePriceRange("abc", "-3");
        assertTrue(result.hasError(ProductValidator.FIELD_MIN_PRICE));
        assertTrue(result.hasError(ProductValidator.FIELD_MAX_PRICE));
    }

    @Test
    void minAboveMaxIsReportedOnTheMaxBox() {
        ValidationResult result = ProductValidator.validatePriceRange("50", "10");
        assertFalse(result.hasError(ProductValidator.FIELD_MIN_PRICE));
        assertEquals("Max price must not be lower than min price.", result.getError(ProductValidator.FIELD_MAX_PRICE));
    }
}
