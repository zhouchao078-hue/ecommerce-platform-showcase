package com.ecommerce.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ecommerce.support.enums.SupportOutboxStatus;
import com.ecommerce.support.mapper.SupportOutboxMapper;
import com.ecommerce.support.model.SupportOutbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportOutboxServiceImplTest {

    @Mock
    private SupportOutboxMapper outboxMapper;

    @InjectMocks
    private SupportOutboxServiceImpl service;

    @Test
    void claimReturnsNothingWhenConditionalUpdateLosesRace() {
        SupportOutbox candidate = new SupportOutbox();
        candidate.setId(1L);
        candidate.setStatus(SupportOutboxStatus.SENDING.name());
        candidate.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(5));

        when(outboxMapper.selectList(any())).thenReturn(List.of(candidate));
        when(outboxMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        List<SupportOutbox> claimed = service.claimPending(10, "RABBIT", 60);

        assertTrue(claimed.isEmpty());
    }

    @Test
    void claimReturnsFreshTokenAndLeaseAfterAtomicWin() {
        SupportOutbox candidate = new SupportOutbox();
        candidate.setId(2L);
        candidate.setStatus(SupportOutboxStatus.PENDING.name());

        when(outboxMapper.selectList(any())).thenReturn(List.of(candidate));
        when(outboxMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        List<SupportOutbox> claimed = service.claimPending(10, "RABBIT", 60);

        assertEquals(1, claimed.size());
        assertEquals(SupportOutboxStatus.SENDING.name(), claimed.get(0).getStatus());
        assertNotNull(claimed.get(0).getClaimToken());
        assertNotNull(claimed.get(0).getLeaseExpiresAt());

        ArgumentCaptor<UpdateWrapper<SupportOutbox>> wrapper =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(outboxMapper).update(isNull(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSet().contains("claim_token"));
        assertTrue(wrapper.getValue().getSqlSet().contains("lease_expires_at"));
    }

    @Test
    void markSentRequiresTheCurrentClaimToken() {
        when(outboxMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        service.markSent(3L, "stale-token");

        ArgumentCaptor<UpdateWrapper<SupportOutbox>> wrapper =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(outboxMapper).update(isNull(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("claim_token"));
        assertFalse(wrapper.getValue().getSqlSet().isBlank());
    }

    @Test
    void markFailedReturnsClaimToPendingOnlyForCurrentOwner() {
        SupportOutbox outbox = new SupportOutbox();
        outbox.setId(4L);
        outbox.setStatus(SupportOutboxStatus.SENDING.name());
        outbox.setClaimToken("current-token");
        outbox.setRetryCount(1);

        when(outboxMapper.selectById(4L)).thenReturn(outbox);
        when(outboxMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.markFailed(4L, "current-token", "publish failed");

        ArgumentCaptor<UpdateWrapper<SupportOutbox>> wrapper =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(outboxMapper).update(isNull(), wrapper.capture());
        String sqlSet = wrapper.getValue().getSqlSet();
        assertTrue(sqlSet.contains("status"));
        assertTrue(sqlSet.contains("claim_token"));
        assertTrue(sqlSet.contains("lease_expires_at"));
        assertTrue(sqlSet.contains("next_retry_at"));
    }
}
