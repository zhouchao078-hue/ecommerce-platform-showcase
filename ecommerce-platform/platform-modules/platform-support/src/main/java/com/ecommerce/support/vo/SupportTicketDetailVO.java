package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SupportTicketDetailVO {
    private SupportTicketVO ticket;
    private List<SupportMessageVO> messages;
}
