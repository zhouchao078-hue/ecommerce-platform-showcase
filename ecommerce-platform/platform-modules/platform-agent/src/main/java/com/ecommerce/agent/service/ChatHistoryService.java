package com.ecommerce.agent.service;

import com.ecommerce.agent.model.ChatMessage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.agent.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体会话历史持久化服务
 * <p>聊天记录存入 MySQL（chat_message 表），重启不丢失，可支撑后续上下文注入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatMessageMapper chatMessageMapper;

    /**
     * 保存一条聊天消息
     */
    public void save(String sessionId, Long userId, String role, String content) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(sessionId);
            msg.setUserId(userId);
            msg.setRole(role);
            msg.setContent(content);
            msg.setCreateTime(LocalDateTime.now());
            chatMessageMapper.insert(msg);
        } catch (Exception e) {
            // 历史记录保存失败不影响主流程
            log.warn("聊天历史保存失败: {}", e.getMessage());
        }
    }

    /**
     * 查询某个会话的历史消息（按时间升序）
     */
    public List<ChatMessage> listBySession(String sessionId, int limit) {
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .orderByDesc("created_at")
                .last("LIMIT " + Math.max(1, limit));
        List<ChatMessage> list = chatMessageMapper.selectList(wrapper);
        // 倒序翻转回时间正序
        java.util.Collections.reverse(list);
        return list;
    }
}
