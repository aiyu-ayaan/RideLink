package com.aiyu.ridelink.core.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountRepositoryTest {
    @Test
    fun `google photo url asks for the wanted size`() {
        assertEquals("https://lh3.googleusercontent.com/a/abc=s256-c", AccountRepository.sized("https://lh3.googleusercontent.com/a/abc=s96-c", 256))
        assertEquals("https://lh3.googleusercontent.com/a/abc=s128-c", AccountRepository.sized("https://lh3.googleusercontent.com/a/abc=s96", 128))
    }

    @Test
    fun `other urls stay as they are`() {
        val url = "https://example.com/me.png"
        assertEquals(url, AccountRepository.sized(url, 256))
    }
}
