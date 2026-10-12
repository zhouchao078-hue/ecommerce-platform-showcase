package com.ecommerce.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.agent.llm.LlmClient;
import com.ecommerce.agent.mapper.ChatMessageMapper;
import com.ecommerce.agent.mapper.ChatSummaryMapper;
import com.ecommerce.agent.mapper.UserProfileMapper;
import com.ecommerce.agent.model.ChatMessage;
import com.ecommerce.agent.model.ChatSummary;
import com.ecommerce.agent.model.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能体记忆服务（长期记忆体系）
 * <p>
 * 三件套：
 * 1. 用户画像（user_profile）：对话后提取可长期记住的用户信息，注入 system；
 * 2. 滚动摘要（chat_summary）：历史超过阈值时把老消息压缩成摘要，避免无限膨胀；
 * 3. 上下文组装：memory 段（画像+摘要，role=memory 由 LlmClient 拼进 system）+ 最近明细。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    /** 超过该消息数触发滚动摘要 */
    private static final int MAX_MESSAGES_BEFORE_SUMMARIZE = 30;
    /** 注入上下文的最近明细条数 */
    private static final int CONTEXT_WINDOW = 10;
    /** 每 N 轮用户对话做一次画像提取（调大更省调用，调小更灵敏） */
    private static final int PROFILE_EXTRACT_EVERY_N_ROUNDS = 5;
    /** 画像提取时回看最近几轮对话 */
    private static final int PROFILE_LOOKBACK_ROUNDS = 5;
    /** 摘要最多保留字数的提示 */
    private static final String SUMMARY_PROMPT =
            "你是对话记忆整理器。把以下内容压缩成一段中文摘要，保留：用户身份、偏好、重要事实、未完成的事。不超过 150 字，口语化。\n内容：\n";
    /** 画像提取提示（增量模式：只提取与现有画像不同的新信息） */
    private static final String PROFILE_EXTRACT_PROMPT =
            "你是用户画像维护器。现有画像与最近对话如下。\n" +
            "任务：提取值得长期记住的用户信息（姓名、爱好、职业、偏好、所在地、家庭等），或对现有画像的修正。\n" +
            "规则：只输出与【现有画像】不同的新信息/更新；忽略临时性内容（如一次性话题、情绪）。\n" +
            "只输出 JSON：{\"items\":[{\"key\":\"hobby\",\"value\":\"喝茶\"}]}，没有新信息则 {\"items\":[]}。\n\n";

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSummaryMapper chatSummaryMapper;
    private final UserProfileMapper userProfileMapper;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    // ==================== 上下文组装 ====================

    /**
     * 组装注入 LLM 的上下文列表：
     * 第一项为 role=memory（画像+摘要，LlmClient 会拼进 system），后续为最近对话明细
     */
    public List<Map<String, String>> buildContext(String sessionId, Long userId) {
        List<Map<String, String>> context = new ArrayList<>();

        // 1. memory 段（画像 + 早前摘要）
        String memory = buildMemoryText(sessionId, userId);
        if (memory != null && !memory.isBlank()) {
            context.add(Map.of("role", "memory", "content", memory));
        }

        // 2. 最近明细（升序）
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .orderByDesc("created_at")
                .last("LIMIT " + CONTEXT_WINDOW);
        List<ChatMessage> recent = chatMessageMapper.selectList(wrapper);
        java.util.Collections.reverse(recent);
        for (ChatMessage m : recent) {
            context.add(Map.of(
                    "role", "user".equals(m.getRole()) ? "user" : "assistant",
                    "content", m.getContent()));
        }
        return context;
    }

    /** 组装 memory 文本：用户画像 + 早前对话摘要 */
    private String buildMemoryText(String sessionId, Long userId) {
        StringBuilder sb = new StringBuilder();
        if (userId != null) {
            String profile = getProfileText(userId);
            if (!profile.isBlank()) {
                sb.append("[用户画像] ").append(profile).append("\n");
            }
        }
        QueryWrapper<ChatSummary> sw = new QueryWrapper<>();
        sw.eq("session_id", sessionId);
        ChatSummary summary = chatSummaryMapper.selectOne(sw);
        if (summary != null && !summary.getSummary().isBlank()) {
            sb.append("[早前对话摘要] ").append(summary.getSummary());
        }
        return sb.toString().trim();
    }

    // ==================== 滚动摘要 ====================

    /**
     * 对话后调用：若历史消息数超过阈值，把最老的一批压缩成摘要并删除原消息。
     * 无 Key 时跳过（LLM 不可用则保留全量，只截取最近窗口）。
     */
    public void maybeSummarize(String sessionId) {
        if (!llmClient.isAvailable()) {
            return;
        }
        QueryWrapper<ChatMessage> countWrapper = new QueryWrapper<>();
        countWrapper.eq("session_id", sessionId);
        Long total = chatMessageMapper.selectCount(countWrapper);
        if (total == null || total < MAX_MESSAGES_BEFORE_SUMMARIZE) {
            return;
        }
        try {
            // 需压缩的条数 = 总条数 - 保留窗口
            int toCompress = total.intValue() - CONTEXT_WINDOW;

            // 取最老的需要压缩的消息
            QueryWrapper<ChatMessage> oldWrapper = new QueryWrapper<>();
            oldWrapper.eq("session_id", sessionId)
                    .orderByAsc("created_at")
                    .last("LIMIT " + toCompress);
            List<ChatMessage> oldMessages = chatMessageMapper.selectList(oldWrapper);
            if (oldMessages.isEmpty()) {
                return;
            }

            // 拼摘要输入：现有摘要 + 老消息
            StringBuilder input = new StringBuilder();
            QueryWrapper<ChatSummary> sw = new QueryWrapper<>();
            sw.eq("session_id", sessionId);
            ChatSummary existing = chatSummaryMapper.selectOne(sw);
            if (existing != null) {
                input.append("[已有摘要] ").append(existing.getSummary()).append("\n\n");
            }
            for (ChatMessage m : oldMessages) {
                input.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
            }

            String summaryText = llmClient.chat(SUMMARY_PROMPT, null, input.toString());
            if (summaryText == null || summaryText.isBlank()) {
                return;
            }

            // 保存/更新摘要
            if (existing != null) {
                existing.setSummary(summaryText);
                chatSummaryMapper.updateById(existing);
            } else {
                ChatSummary cs = new ChatSummary();
                cs.setSessionId(sessionId);
                cs.setSummary(summaryText);
                chatSummaryMapper.insert(cs);
            }

            // 删除已被压缩的旧消息
            List<Long> ids = oldMessages.stream().map(ChatMessage::getId).toList();
            if (!ids.isEmpty()) {
                chatMessageMapper.deleteBatchIds(ids);
            }
            log.info("会话 {} 滚动摘要完成：压缩 {} 条，保留最近 {} 条", sessionId, oldMessages.size(), CONTEXT_WINDOW);
        } catch (Exception e) {
            log.warn("滚动摘要失败: {}", e.getMessage());
        }
    }

    // ==================== 用户画像 ====================

    /** 读取用户画像为一段文本 */
    public String getProfileText(Long userId) {
        QueryWrapper<UserProfile> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByAsc("id");
        List<UserProfile> list = userProfileMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UserProfile p : list) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(p.getProfileKey()).append("：").append(p.getProfileValue());
        }
        return sb.toString();
    }

    /**
     * 对话后调用（周期批量画像提取）：
     * 每 PROFILE_EXTRACT_EVERY_N_ROUNDS 轮用户对话触发一次，
     * 把【现有画像 + 最近几轮对话】交给 LLM 做增量提取，信息更全、调用更省。
     * 无 Key/解析失败时静默跳过。
     */
    public void maybeExtractProfile(String sessionId, Long userId) {
        if (userId == null || !llmClient.isAvailable()) {
            return;
        }
        // 触发条件：该会话的用户消息数正好是 N 的倍数（按"轮"= 用户消息计数）
        QueryWrapper<ChatMessage> roundWrapper = new QueryWrapper<>();
        roundWrapper.eq("session_id", sessionId).eq("role", "user");
        Long userMsgCount = chatMessageMapper.selectCount(roundWrapper);
        if (userMsgCount == null || userMsgCount % PROFILE_EXTRACT_EVERY_N_ROUNDS != 0) {
            return;
        }
        try {
            // 拼输入：现有画像 + 最近几轮对话明细
            StringBuilder input = new StringBuilder();
            String profile = getProfileText(userId);
            input.append("【现有画像】").append(profile.isBlank() ? "（无）" : profile).append("\n\n");

            QueryWrapper<ChatMessage> recentWrapper = new QueryWrapper<>();
            recentWrapper.eq("session_id", sessionId)
                    .orderByDesc("created_at")
                    .last("LIMIT " + (PROFILE_LOOKBACK_ROUNDS * 2));
            List<ChatMessage> recent = chatMessageMapper.selectList(recentWrapper);
            java.util.Collections.reverse(recent);
            input.append("【最近对话】\n");
            for (ChatMessage m : recent) {
                input.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
            }

            String raw = llmClient.chat(PROFILE_EXTRACT_PROMPT, null, input.toString());
            JsonNode root = tryExtractJson(raw);
            if (root == null) {
                log.debug("画像提取：LLM 输出无法解析为 JSON，已跳过（原文: {}）", truncate(raw));
                return;
            }
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return;
            }
            for (JsonNode item : items) {
                String key = item.path("key").asText().trim();
                String value = item.path("value").asText().trim();
                if (key.isEmpty() || value.isEmpty()) {
                    continue;
                }
                saveProfile(userId, key, value);
            }
        } catch (Exception e) {
            log.debug("画像提取跳过: {}", e.getMessage());
        }
    }

    /**
     * LLM 输出 JSON 分级解析兜底：
     * ① 直接解析 → ② 剥离 markdown 代码块/前后废话后提取 {..} 片段 → ③ 仍失败返回 null（调用方静默跳过）
     */
    private JsonNode tryExtractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        // ① 直接解析
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            // 继续尝试
        }
        // ② 剥离可能的 ```json ... ``` 包裹
        try {
            java.util.regex.Matcher fence = java.util.regex.Pattern
                    .compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(text);
            if (fence.find()) {
                return objectMapper.readTree(fence.group(1).trim());
            }
        } catch (Exception ignored) {
        }
        // ③ 截取第一个 { 到最后一个 } 之间的内容
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readTree(text.substring(start, end + 1));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 截断长文本（用于日志） */
    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    /** 保存/更新单个画像项 */
    public void saveProfile(Long userId, String key, String value) {
        QueryWrapper<UserProfile> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("profile_key", key);
        UserProfile existing = userProfileMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setProfileValue(value);
            userProfileMapper.updateById(existing);
        } else {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setProfileKey(key);
            profile.setProfileValue(value);
            profile.setUpdateTime(LocalDateTime.now());
            userProfileMapper.insert(profile);
        }
        log.info("用户画像更新: userId={}, {}={}", userId, key, value);
    }
}
