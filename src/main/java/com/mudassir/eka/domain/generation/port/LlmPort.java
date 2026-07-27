package com.mudassir.eka.domain.generation.port;

import com.mudassir.eka.domain.generation.model.LlmRequest;
import com.mudassir.eka.domain.generation.model.LlmResponse;

public interface LlmPort {

    LlmResponse generate(LlmRequest request);
}
