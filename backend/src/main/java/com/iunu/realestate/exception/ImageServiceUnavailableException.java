package com.iunu.realestate.exception;

/**
 * The image host (Cloudinary) failed or timed out. Maps to 502: the request
 * was fine, the service behind this one was not, and the admin can retry.
 */
public class ImageServiceUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Image service is temporarily unavailable. Please try again.";

    public ImageServiceUnavailableException() {
        super(MESSAGE);
    }
}
