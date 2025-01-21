package io.meeds.chat.service.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordGeneratorTest {

    @Test
    void generatePassword() {
        String generatedPassword = PasswordGenerator.generatePassword(10);
        assertNotNull(generatedPassword);
        assertEquals(10, generatedPassword.length());

        generatedPassword = PasswordGenerator.generatePassword(7);
        assertNotNull(generatedPassword);
        assertEquals(8, generatedPassword.length());

    }
}