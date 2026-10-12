package com.ecommerce.agent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductSearchTool implements AgentTool {

    private final ProductRecommendationTool productRecommendationTool;

    @Override
    public String getName() {
        return "product_search";
    }

    @Override
    public String getCategory() {
        return "product";
    }

    @Override
    public String getDescription() {
        return "Search in-stock products by keyword and return structured product cards.";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of(
                                "type", "string",
                                "description", "Product keyword, category or use case")
                ),
                "required", List.of("keyword")
        );
    }

    @Override
    public String execute(Map<String, Object> args, Long userId) {
        return executeForDisplay(args, userId).content();
    }

    @Override
    public ToolExecutionResult executeForDisplay(Map<String, Object> args, Long userId) {
        return productRecommendationTool.executeForDisplay(args, userId);
    }
}
