package io.github.PhantomDaze.flexibook.parse;

/**
 * Soft parse failure — parser logs and continues rather than throwing to callers.
 */
public class ParseException extends RuntimeException {
    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
