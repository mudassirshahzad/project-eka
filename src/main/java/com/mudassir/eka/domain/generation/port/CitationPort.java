package com.mudassir.eka.domain.generation.port;

import com.mudassir.eka.domain.conversation.Citation;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.shared.TenantId;

import java.util.List;

public interface CitationPort {

    List<Citation> resolve(String generatedText, AssembledContext context, TenantId tenantId);
}
