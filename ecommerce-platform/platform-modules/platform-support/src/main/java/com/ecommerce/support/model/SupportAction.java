package com.ecommerce.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sp_support_action")
public class SupportAction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String actionNo;
    private Long ticketId;
    private String actionType;
    private String status;
    private String payload;

    @TableField("requires_confirmation")
    private Boolean requiresConfirmation;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("executed_at")
    private LocalDateTime executedAt;

    @TableField("result_payload")
    private String resultPayload;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
