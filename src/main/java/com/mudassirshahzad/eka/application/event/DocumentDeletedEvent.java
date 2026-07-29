package com.mudassirshahzad.eka.application.event;

import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.DomainEvent;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public class DocumentDeletedEvent extends DomainEvent {

    private final DocumentId documentId;
    private final TenantId   tenantId;
    private final UserId     deletedBy;

    public DocumentDeletedEvent(DocumentId documentId, TenantId tenantId, UserId deletedBy) {
        super();
        this.documentId = documentId;
        this.tenantId   = tenantId;
        this.deletedBy  = deletedBy;
    }

    @Override
    public String getEventType() { return "document.deleted"; }

    public DocumentId getDocumentId() { return documentId; }
    public TenantId   getTenantId()   { return tenantId; }
    public UserId     getDeletedBy()  { return deletedBy; }
}
