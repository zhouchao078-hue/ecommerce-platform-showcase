package com.ecommerce.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.PdProduct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductRecommendationTool implements AgentTool {

    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "recommend_products";
    }

    @Override
    public String getCategory() {
        return "product";
    }

    @Override
    public String getDescription() {
        return "Recommend real in-stock products, including id, name, price, stock, image and description.";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of(
                                "type", "string",
                                "description", "Optional product keyword, category, use case or budget intent"),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Maximum number of products, default 6")
                )
        );
    }

    @Override
    public String execute(Map<String, Object> args, Long userId) {
        return executeForDisplay(args, userId).content();
    }

    @Override
    public ToolExecutionResult executeForDisplay(Map<String, Object> args, Long userId) {
        String keyword = normalizeKeyword(String.valueOf(args.getOrDefault("keyword", "")));
        int limit = parseLimit(args.get("limit"));
        QueryWrapper<PdProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)
                .gt("stock", 0);
        if (!keyword.isEmpty()) {
            wrapper.and(query -> query
                    .like("name", keyword)
                    .or()
                    .like("description", keyword));
        }
        wrapper.orderByDesc("created_at").last("LIMIT " + limit);
        List<PdProduct> products = productMapper.selectList(wrapper);
        List<ProductCard> cards = products.stream()
                .map(this::toCard)
                .toList();
        try {
            return new ToolExecutionResult(objectMapper.writeValueAsString(cards), cards);
        } catch (Exception ex) {
            return new ToolExecutionResult("[]", cards);
        }
    }

    private ProductCard toCard(PdProduct product) {
        return new ProductCard(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                firstImage(product.getImages()),
                product.getMerchantId(),
                product.getCategoryId(),
                product.getDescription());
    }

    private String firstImage(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(images);
            if (node.isArray() && !node.isEmpty()) {
                return node.get(0).asText();
            }
            if (node.isTextual()) {
                return node.asText();
            }
        } catch (Exception ignored) {
            return images;
        }
        return null;
    }

    private int parseLimit(Object value) {
        if (value instanceof Number number) {
            return Math.max(1, Math.min(12, number.intValue()));
        }
        try {
            return Math.max(1, Math.min(12, Integer.parseInt(String.valueOf(value))));
        } catch (Exception ignored) {
            return 6;
        }
    }

    private String normalizeKeyword(String value) {
        String keyword = value == null ? "" : value.trim();
        String normalized = keyword.toLowerCase();
        if (normalized.equals("product") || normalized.equals("products")
                || normalized.equals("recommend") || normalized.equals("recommendation")
                || keyword.equals("商品") || keyword.equals("产品") || keyword.equals("推荐")) {
            return "";
        }
        return keyword;
    }
}
