package com.mudassirshahzad.eka.application.event;

import com.mudassirshahzad.eka.domain.shared.DomainEvent;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public class UserActivatedEvent extends DomainEvent {

    private final UserId   userId;
    private final TenantId tenantId;

    public UserActivatedEvent(UserId userId, TenantId tenantId) {
        super();
        this.userId   = userId;
        this.tenantId = tenantId;
    }

    @Override
    public String getEventType() { return "user.activated"; }

    public UserId   getUserId()   { return userId; }
    public TenantId getTenantId() { return tenantId; }
}
