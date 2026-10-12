package com.ecommerce.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sp_support_agent_run")
public class SupportAgentRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String runNo;
    private Long ticketId;
    private String status;
    private String provider;
    private String model;
    private String promptVersion;
    private String requestPayload;
    private String responsePayload;
    private String errorMessage;

    @TableField("client_request_id")
    private String clientRequestId;

    @TableField("cancel_requested")
    private Boolean cancelRequested;

    private Integer version;

    @TableField("last_completed_step")
    private String lastCompletedStep;

    @TableField("worker_id")
    private String workerId;

    @TableField("lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @TableField("last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
