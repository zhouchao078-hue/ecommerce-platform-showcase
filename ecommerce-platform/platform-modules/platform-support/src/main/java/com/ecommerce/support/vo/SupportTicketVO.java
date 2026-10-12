package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SupportTicketVO {
    private String ticketNo;
    private String subject;
    private String summary;
    private String category;
    private String priority;
    private Integer status;
    private String statusDesc;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createTime;
}
