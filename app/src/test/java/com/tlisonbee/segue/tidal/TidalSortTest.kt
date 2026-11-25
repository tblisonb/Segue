package com.tlisonbee.segue.tidal

import com.tlisonbee.segue.library.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TidalSortTest {
    private fun item(id: String, title: String, artist: String, album: String, added: Long?) = TidalItem(
        TidalItem.Kind.TRACK, id, title, artist, album, addedAt = added,
        track = Track(TidalTracks.PREFIX + id, 0, 0, title, listOf(artist), album, artist, "", "", null, null, null, null),
    )

    // A playlist in its own order: b was added last, c first.
    private val list = listOf(
        item("a", "Creep", "Radiohead", "Pablo Honey", 2000),
        item("b", "Africa", "Toto", "Toto IV", 3000),
        item("c", "The Boxer", "Simon & Garfunkel", "Bridge over Troubled Water", 1000),
        item("d", "Undated", "Zed", "Zed", null),
    )

    private fun ids(sort: TidalSort) = sort.apply(list).joinToString("") { it.id }

    @Test
    fun albumsArtistsAndPlaylistsByName() {
        val albums = listOf(
            TidalItem(TidalItem.Kind.ALBUM, "1", "Toto IV", "Toto"),
            TidalItem(TidalItem.Kind.ALBUM, "2", "The Bends", "Radiohead"),
            TidalItem(TidalItem.Kind.ALBUM, "3", "Abbey Road", "The Beatles"),
        )
        fun ids(sort: TidalNameSort) = sort.apply(albums).joinToString("") { it.id }
        assertEquals("123", ids(TidalNameSort()))
        assertEquals("321", ids(TidalNameSort(reversed = true)))
        // "The Bends" files under B, as in the library: Abbey Road, The Bends, Toto IV.
        assertEquals("321", ids(TidalNameSort(byName = true)))
        assertEquals("123", ids(TidalNameSort(byName = true, reversed = true)))
    }

    @Test
    fun orders() {
        assertEquals("abcd", ids(TidalSort()))
        assertEquals("bacd", ids(TidalSort(TidalSort.By.ADDED)))
        assertEquals("dcab", ids(TidalSort(TidalSort.By.ADDED, reversed = true)))
        // "The" is ignored when sorting, as in the library.
        assertEquals("bcad", ids(TidalSort(TidalSort.By.TITLE)))
        assertEquals("acbd", ids(TidalSort(TidalSort.By.ARTIST)))
        assertEquals("dcba", ids(TidalSort(reversed = true)))
    }
}
