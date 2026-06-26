package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public class UserDeactivatedEvent extends DomainEvent {

    private final UserId   userId;
    private final TenantId tenantId;

    public UserDeactivatedEvent(UserId userId, TenantId tenantId) {
        super();
        this.userId   = userId;
        this.tenantId = tenantId;
    }

    @Override
    public String getEventType() { return "user.deactivated"; }

    public UserId   getUserId()   { return userId; }
    public TenantId getTenantId() { return tenantId; }
}
