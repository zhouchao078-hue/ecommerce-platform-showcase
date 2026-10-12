package com.ecommerce.support.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketStatusTest {

    @Test
    void mapsPersistedCodeToStatus() {
        assertEquals(TicketStatus.WAITING_MERCHANT, TicketStatus.fromCode(40));
    }

    @Test
    void rejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> TicketStatus.fromCode(999));
    }
}
