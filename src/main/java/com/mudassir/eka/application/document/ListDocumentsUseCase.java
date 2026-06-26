package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListDocumentsUseCase {

    private final DocumentApplicationService documentService;

    public PageResult<Document> execute(TenantId tenantId, PageRequest pageRequest) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return documentService.listDocuments(tenantId, pageRequest);
    }

    public PageResult<Document> executeByOwner(UserId ownerId, TenantId tenantId,
                                               PageRequest pageRequest) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return documentService.listDocumentsByOwner(ownerId, tenantId, pageRequest);
    }
}
