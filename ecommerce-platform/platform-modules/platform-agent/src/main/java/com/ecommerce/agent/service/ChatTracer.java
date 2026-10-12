package com.ecommerce.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.agent.mapper.ChatTraceMapper;
import com.ecommerce.agent.model.ChatTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能体思考轨迹记录器
 * <p>
 * 每次对话开始时 begin() 创建轨迹（ThreadLocal 承载，各层可 current() 追加），
 * 结束时 endAndSave() 落库。轨迹含：意图分类、路由、每轮工具调用（名称/参数/结果）、回复、耗时。
 * 供前端"思考过程控制台"查询展示。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTracer {

    private static final int MAX_CONTENT_LEN = 400;

    private final ChatTraceMapper traceMapper;
    private final ObjectMapper objectMapper;

    /** 一次对话的轨迹构建器 */
    public static class TraceBuilder {
        final String sessionId;
        final Long userId;
        final String message;
        final long startNanos = System.nanoTime();
        String intent;
        String routeAgent;
        final List<Map<String, Object>> steps = new ArrayList<>();

        TraceBuilder(String sessionId, Long userId, String message) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.message = message;
        }

        void recordIntent(String intent) {
            this.intent = intent;
        }

        void recordRoute(String routeAgent) {
            this.routeAgent = routeAgent;
        }

        void recordToolRound(Map<String, Object> step) {
            steps.add(step);
        }
    }

    private static final ThreadLocal<TraceBuilder> CURRENT = new ThreadLocal<>();

    /** 开始一次对话轨迹（在请求线程上） */
    public TraceBuilder begin(String sessionId, Long userId, String message) {
        TraceBuilder builder = new TraceBuilder(sessionId, userId, truncate(message));
        CURRENT.set(builder);
        return builder;
    }

    /** 获取当前线程的轨迹（可能为 null：未在 chat 流程内） */
    public static TraceBuilder current() {
        return CURRENT.get();
    }

    /** 记录意图分类结果 */
    public static void recordIntent(String intent) {
        TraceBuilder b = CURRENT.get();
        if (b != null) {
            b.recordIntent(intent);
        }
    }

    /** 记录路由结果 */
    public static void recordRoute(String agent) {
        TraceBuilder b = CURRENT.get();
        if (b != null) {
            b.recordRoute(agent);
        }
    }

    /** 记录一轮工具调用 */
    public static void recordToolRound(Map<String, Object> step) {
        TraceBuilder b = CURRENT.get();
        if (b != null) {
            b.recordToolRound(step);
        }
    }

    /** 结束并落库（务必在 finally 中调用以保证 ThreadLocal 清理） */
    public void endAndSave(TraceBuilder builder, String reply) {
        try {
            if (builder == null) {
                return;
            }
            ChatTrace trace = new ChatTrace();
            trace.setSessionId(builder.sessionId);
            trace.setUserId(builder.userId);
            trace.setMessage(builder.message);
            trace.setIntent(builder.intent);
            trace.setRouteAgent(builder.routeAgent);
            trace.setSteps(objectMapper.writeValueAsString(builder.steps));
            trace.setReply(truncate(reply));
            trace.setDurationMs((System.nanoTime() - builder.startNanos) / 1_000_000);
            trace.setCreateTime(java.time.LocalDateTime.now());
            traceMapper.insert(trace);
        } catch (Exception e) {
            log.warn("思考轨迹保存失败: {}", e.getMessage());
        } finally {
            CURRENT.remove();
        }
    }

    /** 查询某会话的轨迹（倒序） */
    public List<ChatTrace> listBySession(String sessionId, int limit) {
        QueryWrapper<ChatTrace> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .orderByDesc("created_at")
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return traceMapper.selectList(wrapper);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > MAX_CONTENT_LEN ? s.substring(0, MAX_CONTENT_LEN) + "…" : s;
    }
}
