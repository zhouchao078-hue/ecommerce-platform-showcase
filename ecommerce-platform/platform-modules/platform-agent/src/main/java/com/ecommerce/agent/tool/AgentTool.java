package com.ecommerce.agent.tool;

import java.util.List;
import java.util.Map;

/**
 * 智能体工具接口（Function Calling）
 * <p>
 * LLM 根据工具描述决定调用哪个工具、传什么参数，
 * 工具在服务端安全执行（携带当前用户身份），结果喂回 LLM 组织回答。
 * </p>
 */
public interface AgentTool {

    /** 工具名称（LLM 调用时使用，如 product_search） */
    String getName();

    /**
     * 工具所属业务域（分层路由用：只把同域工具暴露给 LLM，保证选择准确率）。
     * <p>如 ProductSearchTool → "product"，OrderListTool → "order"，CartListTool → "cart"</p>
     */
    default String getCategory() {
        return "general";
    }

    /** 工具说明（LLM 判断何时调用，建议写明触发场景） */
    String getDescription();

    /**
     * 参数 JSON Schema（OpenAI function calling 格式）
     * <pre>
     * Map.of(
     *   "type", "object",
     *   "properties", Map.of("keyword", Map.of("type", "string", "description", "搜索关键词")),
     *   "required", List.of("keyword")
     * )
     * </pre>
     */
    Map<String, Object> getParameters();

    /**
     * 执行工具（返回给 LLM 的结构化文本/JSON）
     *
     * @param args    LLM 生成的参数
     * @param userId  当前登录用户ID
     * @return 工具结果（建议 JSON，便于 LLM 组织语言）
     */
    String execute(Map<String, Object> args, Long userId);

    /**
     * Execute a tool while also exposing structured data that the frontend can render.
     */
    default ToolExecutionResult executeForDisplay(Map<String, Object> args, Long userId) {
        return new ToolExecutionResult(execute(args, userId), List.of());
    }
}
