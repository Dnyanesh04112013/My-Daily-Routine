package com.example

import com.example.auth.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `hashPassword and verifyPassword validate matching and reject wrong passwords`() {
        val (salt, hash) = PasswordHasher.hashPassword("mySecret123")
        assertTrue(PasswordHasher.verifyPassword("mySecret123", salt, hash))
        assertFalse(PasswordHasher.verifyPassword("wrongPassword", salt, hash))
        assertFalse(PasswordHasher.verifyPassword("", salt, hash))
    }
}
