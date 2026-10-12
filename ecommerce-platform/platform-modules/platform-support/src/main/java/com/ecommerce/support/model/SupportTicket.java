package com.ecommerce.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sp_support_ticket")
public class SupportTicket {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private Long userId;
    private Long merchantId;
    private Long orderId;
    private Long afterSaleId;
    private String channel;
    private String clientRequestId;
    private String category;
    private String priority;
    private Integer status;
    private String subject;
    private String summary;
    private String assigneeType;
    private Long assigneeId;

    @TableField("sla_deadline")
    private LocalDateTime slaDeadline;

    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
