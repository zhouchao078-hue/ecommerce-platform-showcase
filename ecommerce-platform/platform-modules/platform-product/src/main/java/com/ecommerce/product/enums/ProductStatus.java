package com.ecommerce.product.enums;

public enum ProductStatus {
    CLOSED(0, "已下架"),
    OPEN(1, "已上架"),;
    private final int code;
    private final String desc;
    ProductStatus(int code, String desc){
        this.code = code;
        this.desc = desc;
    }
    public int getCode() {
        return code;
    }
    public String getDesc() {
        return desc;
    }
    public static ProductStatus fromCode(int code) {
        for (ProductStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("商品状态: " + code);
    }
}
