package com.aiyu.ridelink.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubProfileTest {
    @Test fun parsesFieldsAndTreatsNullAsMissing() {
        val u = GitHubProfile.parse(
            """{"login":"aiyu-ayaan","name":"Ayaan","bio":null,"avatar_url":"https://x/a.png","location":"","followers":12,"public_repos":7,"html_url":"https://github.com/aiyu-ayaan"}""",
        )
        assertEquals("aiyu-ayaan", u.login)
        assertEquals("Ayaan", u.name)
        assertNull(u.bio)
        assertNull(u.location)
        assertEquals(12, u.followers)
        assertEquals(7, u.publicRepos)
    }
}
