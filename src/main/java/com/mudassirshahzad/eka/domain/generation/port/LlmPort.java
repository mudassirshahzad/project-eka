package com.mudassirshahzad.eka.domain.generation.port;

import com.mudassirshahzad.eka.domain.generation.model.LlmRequest;
import com.mudassirshahzad.eka.domain.generation.model.LlmResponse;

public interface LlmPort {

    LlmResponse generate(LlmRequest request);
}
