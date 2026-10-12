package com.ecommerce.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ecommerce.support.enums.SupportOutboxStatus;
import com.ecommerce.support.mapper.SupportOutboxMapper;
import com.ecommerce.support.model.SupportOutbox;
import com.ecommerce.support.service.SupportOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportOutboxServiceImpl implements SupportOutboxService {

    private final SupportOutboxMapper outboxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enqueue(String aggregateType, Long aggregateId, String eventType,
                        String transport, String destination, String payload) {
        LocalDateTime now = LocalDateTime.now();
        SupportOutbox outbox = new SupportOutbox();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTransport(transport);
        outbox.setDestination(destination);
        outbox.setPayload(payload);
        outbox.setStatus(SupportOutboxStatus.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);
        outbox.setClaimToken(null);
        outbox.setLeaseExpiresAt(null);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        outboxMapper.insert(outbox);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SupportOutbox> claimPending(int limit, String transport, long leaseSeconds) {
        LocalDateTime now = LocalDateTime.now();
        List<SupportOutbox> candidates = outboxMapper.selectList(new QueryWrapper<SupportOutbox>()
                .eq("transport", transport)
                .le("next_retry_at", now)
                .and(wrapper -> wrapper
                        .eq("status", SupportOutboxStatus.PENDING.name())
                        .or()
                        .eq("status", SupportOutboxStatus.SENDING.name())
                        .and(lease -> lease
                                .isNull("lease_expires_at")
                                .or()
                                .lt("lease_expires_at", now)))
                .orderByAsc("id")
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        List<SupportOutbox> claimed = new ArrayList<>();
        for (SupportOutbox candidate : candidates) {
            String claimToken = UUID.randomUUID().toString();
            LocalDateTime leaseExpiresAt = now.plusSeconds(normalizeLeaseSeconds(leaseSeconds));
            int updated = outboxMapper.update(null, new UpdateWrapper<SupportOutbox>()
                    .eq("id", candidate.getId())
                    .eq("transport", transport)
                    .le("next_retry_at", now)
                    .and(wrapper -> wrapper
                            .eq("status", SupportOutboxStatus.PENDING.name())
                            .or()
                            .eq("status", SupportOutboxStatus.SENDING.name())
                            .and(lease -> lease
                                    .isNull("lease_expires_at")
                                    .or()
                                    .lt("lease_expires_at", now)))
                    .set("status", SupportOutboxStatus.SENDING.name())
                    .set("claim_token", claimToken)
                    .set("lease_expires_at", leaseExpiresAt)
                    .set("updated_at", now));
            if (updated == 1) {
                candidate.setStatus(SupportOutboxStatus.SENDING.name());
                candidate.setClaimToken(claimToken);
                candidate.setLeaseExpiresAt(leaseExpiresAt);
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSent(Long id, String claimToken) {
        int updated = outboxMapper.update(null, new UpdateWrapper<SupportOutbox>()
                .eq("id", id)
                .eq("status", SupportOutboxStatus.SENDING.name())
                .eq("claim_token", claimToken)
                .set("status", SupportOutboxStatus.SENT.name())
                .set("claim_token", null)
                .set("lease_expires_at", null)
                .set("last_error", null)
                .set("updated_at", LocalDateTime.now()));
        if (updated == 0) {
            log.warn("Outbox claim was lost before markSent: id={}", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long id, String claimToken, String errorMessage) {
        SupportOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null
                || !SupportOutboxStatus.SENDING.name().equals(outbox.getStatus())
                || claimToken == null
                || !claimToken.equals(outbox.getClaimToken())) {
            log.warn("Outbox claim was lost before markFailed: id={}", id);
            return;
        }
        int retryCount = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        long delaySeconds = Math.min(300, 5L * (1L << Math.min(retryCount, 6)));
        int updated = outboxMapper.update(null, new UpdateWrapper<SupportOutbox>()
                .eq("id", id)
                .eq("status", SupportOutboxStatus.SENDING.name())
                .eq("claim_token", claimToken)
                .set("status", SupportOutboxStatus.PENDING.name())
                .set("retry_count", retryCount)
                .set("next_retry_at", LocalDateTime.now().plusSeconds(delaySeconds))
                .set("claim_token", null)
                .set("lease_expires_at", null)
                .set("last_error", errorMessage)
                .set("updated_at", LocalDateTime.now()));
        if (updated == 0) {
            log.warn("Outbox claim was lost before retry scheduling: id={}", id);
        }
    }

    private long normalizeLeaseSeconds(long leaseSeconds) {
        return Math.max(1L, Math.min(leaseSeconds, 3600L));
    }
}
