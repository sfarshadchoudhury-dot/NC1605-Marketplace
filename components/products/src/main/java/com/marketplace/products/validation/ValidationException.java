package com.marketplace.products.validation;

/** Thrown by the service layer when a form fails validation (it carries the field-level errors). */
public class ValidationException extends RuntimeException {

    private final ValidationResult result;

    public ValidationException(ValidationResult result) {
        super("Validation failed: " + result);
        this.result = result;
    }

    public ValidationResult getResult() {
        return result;
    }
}
