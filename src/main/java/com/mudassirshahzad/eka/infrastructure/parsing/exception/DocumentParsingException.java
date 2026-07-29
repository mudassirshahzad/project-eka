package com.mudassirshahzad.eka.infrastructure.parsing.exception;

public class DocumentParsingException extends RuntimeException {

    public DocumentParsingException(String message) {
        super(message);
    }

    public DocumentParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
