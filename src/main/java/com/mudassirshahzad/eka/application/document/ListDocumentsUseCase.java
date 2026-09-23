package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListDocumentsUseCase {

    private final DocumentApplicationService documentService;

    public PageResult<Document> execute(TenantId tenantId, PageRequest pageRequest, Set<UserRole> roles) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        return documentService.listDocuments(tenantId, pageRequest, roles);
    }

    public PageResult<Document> executeByOwner(UserId ownerId, TenantId tenantId,
                                               PageRequest pageRequest, Set<UserRole> roles) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        return documentService.listDocumentsByOwner(ownerId, tenantId, pageRequest, roles);
    }
}
