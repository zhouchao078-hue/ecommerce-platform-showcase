package com.ecommerce.support.service;

import com.ecommerce.support.model.SupportOutbox;

import java.util.List;

public interface SupportOutboxService {

    void enqueue(String aggregateType, Long aggregateId, String eventType,
                 String transport, String destination, String payload);

    List<SupportOutbox> claimPending(int limit, String transport, long leaseSeconds);

    void markSent(Long id, String claimToken);

    void markFailed(Long id, String claimToken, String errorMessage);
}
