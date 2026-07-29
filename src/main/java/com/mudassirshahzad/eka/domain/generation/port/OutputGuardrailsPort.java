package com.mudassirshahzad.eka.domain.generation.port;

import com.mudassirshahzad.eka.domain.generation.model.GuardrailResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;

public interface OutputGuardrailsPort {

    GuardrailResult apply(String generatedText, TenantId tenantId);
}
