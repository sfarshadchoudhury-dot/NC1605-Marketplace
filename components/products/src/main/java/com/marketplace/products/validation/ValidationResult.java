package com.marketplace.products.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The outcome of validating a form: one error message per field name.
 * An empty result means the input is valid.
 */
public class ValidationResult {

    // LinkedHashMap keeps the errors in the order they were added (form order).
    private final Map<String, String> errors = new LinkedHashMap<>();

    public void addError(String field, String message) {
        errors.put(field, message);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasError(String field) {
        return errors.containsKey(field);
    }

    /** Returns the message for the field, or null when that field is fine. */
    public String getError(String field) {
        return errors.get(field);
    }

    public Map<String, String> getErrors() {
        return Collections.unmodifiableMap(errors);
    }

    /** The first field that failed, or null when valid. Used to move keyboard focus. */
    public String getFirstErrorField() {
        for (String field : errors.keySet()) {
            return field;
        }
        return null;
    }

    @Override
    public String toString() {
        return errors.toString();
    }
}
