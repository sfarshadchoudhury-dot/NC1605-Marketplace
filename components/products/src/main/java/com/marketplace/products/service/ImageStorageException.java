package com.marketplace.products.service;

/** An image was rejected (wrong type, too big, unreadable) or could not be copied. The message is user-friendly. */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message) {
        super(message);
    }

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
