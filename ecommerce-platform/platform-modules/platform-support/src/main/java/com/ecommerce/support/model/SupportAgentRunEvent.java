package com.ecommerce.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sp_support_agent_run_event")
public class SupportAgentRunEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("run_id")
    private Long runId;

    @TableField("event_type")
    private String eventType;

    private String status;

    @TableField("last_completed_step")
    private String lastCompletedStep;

    @TableField("worker_id")
    private String workerId;

    @TableField("lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @TableField("last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @TableField("error_message")
    private String errorMessage;

    private String payload;

    @TableField("created_at")
    private LocalDateTime createTime;
}
