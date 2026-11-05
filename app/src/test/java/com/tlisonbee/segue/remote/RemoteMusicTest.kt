package com.tlisonbee.segue.remote

import com.tlisonbee.segue.library.LibraryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMusicTest {
    /** A track as MediaSync's server sends it in GET /api/v1/music, with fields Segue doesn't read. */
    private val json = """
        {"rev":12,"tracks":[{"assetId":"a1","rev":7,"deleted":false,"path":"music/Boston/Third Stage/05 My Destination","kind":"audio",
          "category":null,"renditions":[{"mediaHash":"m1","blob":"b1","format":"mp3","size":5000000,"durationMs":221000}],
          "tags":{"title":["My Destination"],"artist":["Boston"],"album":["Third Stage"],"albumartist":["Boston"],"date":["1986"],
                  "tracknumber":["5"],"replaygain_track_gain":["-6.02 dB"],"replaygain_album_peak":["0.988000"]},
          "pictures":[{"type":"front","blob":"c1","mime":"image/jpeg"}]}],"unchanged":false}
    """.trimIndent()
    private val dto = ServerJson.decodeFromString(MusicListing.serializer(), json).tracks.single()

    @Test
    fun aServerTrackBecomesAStreamedTrack() {
        val t = RemoteMusic.toTrack(dto, "/storage/emulated/0/Music")!!
        assertEquals("mediasync:a1", t.path)
        assertEquals("My Destination", t.title)
        assertEquals("1986", t.year)
        assertEquals(5, t.trackNumber)
        assertEquals(221_000L, t.durationMs)
        assertEquals("rc1", t.cover)
        assertEquals(-6.02f, t.gain.trackGain!!, 1e-4f)
        assertEquals(0.988f, t.gain.albumPeak!!, 1e-4f)
        assertNull(t.gain.albumGain)
        assertEquals("b1", t.stream!!.blob)
        // Where MediaSync would put it, to tell whether the phone already has it.
        assertEquals("/storage/emulated/0/Music/Boston/Third Stage/05 My Destination.mp3", t.stream!!.localPath)
        assertNull(RemoteMusic.toTrack(dto.copy(renditions = emptyList()), null))
        assertNull(RemoteMusic.toTrack(dto, null)!!.stream!!.localPath)
    }

    @Test
    fun serverTracksJoinAlbumsButNotFolders() {
        val t = RemoteMusic.toTrack(dto, "/storage/emulated/0/Music")!!
        val data = LibraryData(listOf(t))
        assertEquals(listOf(t), data.albums.single().tracks)
        val root = data.folder("/storage/emulated/0/Music")
        assertEquals(0, root.tracks.size + root.folders.size)
        assertEquals(listOf(t), data.search("destination").tracks)
    }
}
