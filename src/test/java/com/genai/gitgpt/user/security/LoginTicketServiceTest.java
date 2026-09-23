package com.genai.gitgpt.user.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoginTicketServiceTest {

    @Test
    void consumeIsOneShot() {
        LoginTicketService tickets = new LoginTicketService();
        String ticket = tickets.issue("42");
        assertEquals("42", tickets.consume(ticket));
        assertNull(tickets.consume(ticket));
    }

    @Test
    void unknownTicketIsNull() {
        LoginTicketService tickets = new LoginTicketService();
        assertNull(tickets.consume("not-a-ticket"));
        assertNotEquals("42", tickets.issue("42"));
    }
}
