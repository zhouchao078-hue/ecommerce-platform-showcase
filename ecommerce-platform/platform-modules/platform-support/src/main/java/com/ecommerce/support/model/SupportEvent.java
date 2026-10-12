package com.ecommerce.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sp_support_event")
public class SupportEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String eventType;

    @TableField("from_status")
    private Integer fromStatus;

    @TableField("to_status")
    private Integer toStatus;

    private String actorType;
    private Long actorId;
    private String payload;

    @TableField("created_at")
    private LocalDateTime createTime;
}
