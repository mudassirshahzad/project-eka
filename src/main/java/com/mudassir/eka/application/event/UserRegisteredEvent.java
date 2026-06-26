package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public class UserRegisteredEvent extends DomainEvent {

    private final UserId   userId;
    private final TenantId tenantId;
    private final String   email;

    public UserRegisteredEvent(UserId userId, TenantId tenantId, String email) {
        super();
        this.userId   = userId;
        this.tenantId = tenantId;
        this.email    = email;
    }

    @Override
    public String getEventType() { return "user.registered"; }

    public UserId   getUserId()   { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String   getEmail()    { return email; }
}
