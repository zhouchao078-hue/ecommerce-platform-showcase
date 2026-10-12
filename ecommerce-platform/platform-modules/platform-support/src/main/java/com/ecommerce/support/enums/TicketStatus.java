package com.ecommerce.support.enums;

/**
 * Persistent ticket state. The numeric codes are stored in MySQL.
 */
public enum TicketStatus {
    NEW(10, "Pending triage"),
    TRIAGED(20, "Triaged"),
    WAITING_USER(30, "Waiting for user"),
    WAITING_MERCHANT(40, "Waiting for merchant"),
    WAITING_PLATFORM(50, "Waiting for platform"),
    WAITING_HUMAN(60, "Waiting for human support"),
    RESOLVED(70, "Resolved"),
    CLOSED(80, "Closed");

    private final int code;
    private final String description;

    TicketStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TicketStatus fromCode(Integer code) {
        if (code != null) {
            for (TicketStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Unknown ticket status: " + code);
    }
}
