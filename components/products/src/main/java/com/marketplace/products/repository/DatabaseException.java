package com.marketplace.products.repository;

/**
 * The database could not be reached or an operation on it failed.
 * The message is written for the user and says what to do next.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
