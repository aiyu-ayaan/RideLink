package com.aiyu.ridelink.core.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The public fields of a GitHub user the About page shows. */
data class GitHubUser(
    val login: String,
    val name: String?,
    val bio: String?,
    val avatarUrl: String?,
    val location: String?,
    val followers: Int,
    val publicRepos: Int,
    val htmlUrl: String?,
)

object GitHubProfile {
    /** Pure parse of `GET /users/{login}`, so it can be tested without a network. */
    fun parse(json: String): GitHubUser {
        val o = JSONObject(json)
        fun str(key: String) = o.optString(key, "").takeIf { it.isNotBlank() && it != "null" }
        return GitHubUser(
            login = o.getString("login"),
            name = str("name"),
            bio = str("bio"),
            avatarUrl = str("avatar_url"),
            location = str("location"),
            followers = o.optInt("followers"),
            publicRepos = o.optInt("public_repos"),
            htmlUrl = str("html_url"),
        )
    }

    /** Public API, no key. Null when offline or rate limited (60 requests an hour per IP). */
    suspend fun fetch(login: String): GitHubUser? = withContext(Dispatchers.IO) {
        runCatching { parse(get("https://api.github.com/users/$login").toString(Charsets.UTF_8)) }.getOrNull()
    }

    /** Avatar bytes, 200 px is plenty for a 96 dp image. */
    suspend fun avatar(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { get("$url${if ('?' in url) "&" else "?"}s=200") }.getOrNull()
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "RideLink") // GitHub rejects requests without one
        return try {
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
