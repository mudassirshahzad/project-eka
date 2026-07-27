package com.mudassir.eka.application.generation;

import com.mudassir.eka.application.shared.ApplicationException;

public class GenerationException extends ApplicationException {

    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
