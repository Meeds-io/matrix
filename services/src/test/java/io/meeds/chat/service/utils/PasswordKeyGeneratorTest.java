package io.meeds.chat.service.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordKeyGeneratorTest {

    @Test
    void generatePassword() {
        String generatedPassword = PasswordKeyGenerator.generatePassword(10);
        assertNotNull(generatedPassword);
        assertEquals(10, generatedPassword.length());

        generatedPassword = PasswordKeyGenerator.generatePassword(7);
        assertNotNull(generatedPassword);
        assertEquals(8, generatedPassword.length());

    }
}