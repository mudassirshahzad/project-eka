package com.mudassir.eka.application.retrieval;

import com.mudassir.eka.application.shared.ApplicationException;

public class RetrievalException extends ApplicationException {

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
