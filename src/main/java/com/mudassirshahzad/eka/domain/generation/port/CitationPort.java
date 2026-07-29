package com.mudassirshahzad.eka.domain.generation.port;

import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.shared.TenantId;

import java.util.List;

public interface CitationPort {

    List<Citation> resolve(String generatedText, AssembledContext context, TenantId tenantId);
}
