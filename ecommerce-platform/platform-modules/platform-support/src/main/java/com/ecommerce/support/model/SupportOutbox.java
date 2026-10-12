package com.ecommerce.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sp_support_outbox")
public class SupportOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("aggregate_type")
    private String aggregateType;

    @TableField("aggregate_id")
    private Long aggregateId;

    @TableField("event_type")
    private String eventType;

    private String transport;

    private String destination;

    private String payload;

    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("claim_token")
    private String claimToken;

    @TableField("lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @TableField("last_error")
    private String lastError;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
