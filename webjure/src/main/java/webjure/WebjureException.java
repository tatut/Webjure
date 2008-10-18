package webjure;

/**
 * An unchecked exception for wrapping failures.
 * Webjure does not have exceptional situations in itself so
 * failures always have an underlying cause (user clojure code,
 * servlet, io, etc).
 */
public class WebjureException extends RuntimeException {
    public WebjureException(String message, Throwable cause) {
	super(message, cause);
    }
}