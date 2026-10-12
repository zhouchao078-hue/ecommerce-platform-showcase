package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SupportMessageVO {
    private Long id;
    private String senderType;
    private String messageType;
    private String content;
    private String visibility;
    private String clientMessageId;
    private Boolean duplicate;
    private LocalDateTime createTime;
}
