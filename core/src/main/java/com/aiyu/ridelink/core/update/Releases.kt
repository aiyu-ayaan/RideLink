package com.aiyu.ridelink.core.update

/**
 * What a GitHub release is, and which one of them this phone should install (same rules as Rewire).
 *
 * Pure except [Releases.parse]: no network, no clock. The release workflow names versions `1.0.1-alpha.1`,
 * `1.0.1-beta.1` and `1.0.1`, and attaches `RideLink-<version>.apk`. See `.github/workflows/release.yml`.
 */

/** Which builds a phone is willing to be offered. */
enum class UpdateChannel(internal val stage: Int) {
    STABLE(Version.STABLE),
    BETA(Version.BETA),
    ALPHA(Version.ALPHA);

    /** A channel takes its own builds and everything steadier, so beta still sees the stable that supersedes it. */
    fun accepts(version: Version): Boolean = version.stage >= stage

    companion object {
        /** Default channel: the one this build came from, so an alpha install isn't stranded on stable. */
        fun forVersion(versionName: String?): UpdateChannel = when (Version.parse(versionName)?.stage) {
            Version.ALPHA -> ALPHA
            Version.BETA -> BETA
            else -> STABLE
        }
    }
}

/**
 * Ordered semantic version. Only what the release workflow produces is understood (`1.2.3`, `1.2.3-alpha.4`,
 * `1.2.3-beta.4`, optional leading `v`); anything else parses to null and is skipped, because a guess here
 * installs the wrong APK.
 */
data class Version(val major: Int, val minor: Int, val patch: Int, val stage: Int, val stageNumber: Int) : Comparable<Version> {

    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.stage }, { it.stageNumber })

    companion object {
        const val ALPHA = 0
        const val BETA = 1
        const val STABLE = 2

        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$""", RegexOption.IGNORE_CASE)

        fun parse(text: String?): Version? {
            val match = PATTERN.matchEntire(text?.trim().orEmpty()) ?: return null
            val (major, minor, patch, label, number) = match.destructured
            return Version(
                major.toInt(), minor.toInt(), patch.toInt(),
                stage = when (label.lowercase()) { "alpha" -> ALPHA; "beta" -> BETA; else -> STABLE },
                // A stable release has no pre-release number and must still beat `-beta.9` of the same version.
                stageNumber = number.toIntOrNull() ?: Int.MAX_VALUE,
            )
        }
    }
}

/** [sha256] is the lowercase hex digest GitHub records for the file, when it does. */
data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String? = null)

data class Release(val version: Version, val name: String, val notes: String, val publishedAt: String, val assets: List<ReleaseAsset>)

object Releases {
    /** A constant, not a setting: this project signs the builds, and Android refuses an update signed by another key. */
    const val REPOSITORY = "aiyu-ayaan/RideLink"
    const val API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=30"
    const val PAGE = "https://github.com/$REPOSITORY/releases"

    /** Newest release on [channel] newer than [installed], by version and never by publish date. */
    fun pick(releases: List<Release>, installed: Version?, channel: UpdateChannel): Release? =
        releases.filter { channel.accepts(it.version) && (installed == null || it.version > installed) }.maxByOrNull { it.version }

    /** `RideLink-<version>.apk`. A release without one (its Android job failed) is not an offer. */
    fun apk(release: Release): ReleaseAsset? =
        release.assets.firstOrNull { it.name.startsWith("RideLink-") && it.name.endsWith(".apk", ignoreCase = true) }

    /** The releases GitHub returned, minus drafts and tags this app doesn't understand. */
    fun parse(body: String): List<Release> {
        val list = org.json.JSONArray(body)
        return (0 until list.length()).mapNotNull { i ->
            val r = list.getJSONObject(i)
            if (r.optBoolean("draft")) return@mapNotNull null
            val tag = r.optString("tag_name")
            val version = Version.parse(tag) ?: return@mapNotNull null
            val assets = r.optJSONArray("assets")
            Release(
                version = version,
                name = r.optString("name").ifBlank { tag }.removePrefix("v"),
                notes = r.optString("body"),
                publishedAt = r.optString("published_at"),
                assets = (0 until (assets?.length() ?: 0)).map { j ->
                    val a = assets!!.getJSONObject(j)
                    ReleaseAsset(
                        a.optString("name"), a.optString("browser_download_url"), a.optLong("size"),
                        a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                    )
                },
            )
        }
    }
}

/** One drawable piece of a release body, which `scripts/release-notes.mjs` writes as `### Heading` + `* bullet` lines. */
sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Bullet(val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
}

object ReleaseNotes {
    /** Headings, bullets, paragraphs. Table rules (`| --- |`) and fences are dropped rather than drawn as noise. */
    fun parse(text: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        var inFence = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("```")) { inFence = !inFence; continue }
            when {
                inFence || line.isEmpty() || line.matches(TABLE_RULE) -> Unit
                line.startsWith("#") -> blocks += NoteBlock.Heading(line.trimStart('#').trim())
                line.startsWith("* ") || line.startsWith("- ") -> blocks += NoteBlock.Bullet(line.drop(2).trim())
                else -> blocks += NoteBlock.Paragraph(line)
            }
        }
        return blocks
    }

    private val TABLE_RULE = Regex("""^\|?[\s|:-]+\|?$""")
}
