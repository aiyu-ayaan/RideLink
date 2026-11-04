package com.aiyu.ridelink.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String, vararg assets: String) =
        Release(Version.parse(tag)!!, tag, "", "", assets.map { ReleaseAsset(it, "https://x/$it", 1) })

    @Test fun versionOrder() {
        val v = listOf("0.2.0", "0.2.1-alpha.1", "0.2.1-alpha.2", "0.2.1-beta.1", "0.2.1", "v1.0.0").map { Version.parse(it)!! }
        assertEquals(v, v.shuffled().sorted())
        assertNull(Version.parse("0.2"))
        assertNull(Version.parse("1.0.0-rc.1"))
    }

    @Test fun channelPicksNewestItAccepts() {
        val all = listOf(release("0.2.1-alpha.1"), release("0.2.1-beta.1"), release("0.2.0"))
        val installed = Version.parse("0.2.0")
        // Alpha takes everything steadier too, and beta.1 is newer than alpha.1.
        assertEquals("0.2.1-beta.1", Releases.pick(all, installed, UpdateChannel.ALPHA)!!.name)
        assertEquals("0.2.1-alpha.1", Releases.pick(all.take(1), installed, UpdateChannel.ALPHA)!!.name)
        assertEquals("0.2.1-beta.1", Releases.pick(all, installed, UpdateChannel.BETA)!!.name)
        assertNull(Releases.pick(all, installed, UpdateChannel.STABLE))
        assertEquals(UpdateChannel.ALPHA, UpdateChannel.forVersion("0.2.1-alpha.3"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.forVersion("0.2.0"))
    }

    @Test fun onlyTheRideLinkApkIsOffered() {
        assertEquals("RideLink-0.3.0.apk", Releases.apk(release("0.3.0", "notes.txt", "RideLink-0.3.0.apk"))!!.name)
        assertNull(Releases.apk(release("0.3.0", "app-release.aab")))
    }

    @Test fun notesBlocks() {
        val blocks = ReleaseNotes.parse("## [0.3.0](x)\n\n### Features\n\n* Add `pill` bar\n| --- |\nText")
        assertEquals(NoteBlock.Heading("[0.3.0](x)"), blocks[0])
        assertEquals(NoteBlock.Bullet("Add `pill` bar"), blocks[2])
        assertTrue(blocks.last() is NoteBlock.Paragraph)
    }
}
