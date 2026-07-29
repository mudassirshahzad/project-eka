package com.mudassirshahzad.eka.application.retrieval;

import com.mudassirshahzad.eka.application.shared.ApplicationException;

public class RetrievalException extends ApplicationException {

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
