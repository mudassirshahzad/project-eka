package com.mudassirshahzad.eka.application.shared;

import com.mudassirshahzad.eka.domain.shared.DomainEvent;

/**
 * Outbound port for publishing domain events from the application layer.
 * Infrastructure provides the implementation via {@code SpringDomainEventPublisher}.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
