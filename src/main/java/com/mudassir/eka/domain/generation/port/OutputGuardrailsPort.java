package com.mudassir.eka.domain.generation.port;

import com.mudassir.eka.domain.generation.model.GuardrailResult;
import com.mudassir.eka.domain.shared.TenantId;

public interface OutputGuardrailsPort {

    GuardrailResult apply(String generatedText, TenantId tenantId);
}
