package com.ecommerce.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.agent.model.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
