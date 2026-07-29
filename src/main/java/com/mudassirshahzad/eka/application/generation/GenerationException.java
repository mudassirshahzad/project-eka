package com.mudassirshahzad.eka.application.generation;

import com.mudassirshahzad.eka.application.shared.ApplicationException;

public class GenerationException extends ApplicationException {

    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
