package tech.csm.exception;

import org.springframework.http.HttpStatus;

/**
 * Jab caller ko ek specific HTTP status + readable message dena ho
 * (400 galat value, 404 nahi mila, 409 conflict, 503 busy).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
