package com.ecommerce.merchant.enums;

public enum MerchantStatus {
    PENDING(0, "待审核"),
    ACTIVE(1, "已开通"),
    CLOSED(2, "已关闭");
    private final int code;
    private final String desc;
    MerchantStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
    public static MerchantStatus fromCode(int code) {
        for (MerchantStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的商家状态: " + code);
    }
}
