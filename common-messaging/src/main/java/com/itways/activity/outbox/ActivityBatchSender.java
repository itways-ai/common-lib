package com.itways.activity.outbox;

import com.itways.activity.dto.AccountActivityEvent;
import java.util.List;

/**
 * Sends a batch of activity events and returns only once the broker has
 * confirmed every one of them; throws otherwise (then none of the batch counts as
 * sent, and the relay tries it again later).
 */
@FunctionalInterface
public interface ActivityBatchSender {

    void send(List<AccountActivityEvent> events);
}
