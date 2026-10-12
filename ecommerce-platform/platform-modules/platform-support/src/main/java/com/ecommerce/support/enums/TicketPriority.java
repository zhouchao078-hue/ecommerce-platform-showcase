package com.ecommerce.support.enums;

public enum TicketPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public static TicketPriority fromCode(String code) {
        if (code == null) {
            return NORMAL;
        }
        for (TicketPriority priority : values()) {
            if (priority.name().equalsIgnoreCase(code)) {
                return priority;
            }
        }
        return NORMAL;
    }
}
