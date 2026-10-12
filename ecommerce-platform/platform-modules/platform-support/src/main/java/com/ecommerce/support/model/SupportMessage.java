package com.ecommerce.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sp_support_message")
public class SupportMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String senderType;
    private Long senderId;
    private String messageType;
    private String content;

    @TableField("structured_payload")
    private String structuredPayload;

    @TableField("client_message_id")
    private String clientMessageId;

    private String visibility;

    @TableField("trace_id")
    private String traceId;

    @TableField("created_at")
    private LocalDateTime createTime;
}
