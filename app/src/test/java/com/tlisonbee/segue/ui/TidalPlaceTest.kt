package com.tlisonbee.segue.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TidalPlaceTest {
    @Test
    fun pagesAndSearchComeBack() {
        val place = TidalPlace(
            TidalTab.PLAYLISTS, "boards of \"canada\"", searched = true,
            listOf(Route.TidalArtist("1", "Boards of Canada"), Route.TidalAlbum("2", "Geogaddi"), Route.TidalPlaylist("p-3", "Mine")),
        )
        assertEquals(place, TidalPlace.fromJson(place.toJson()))
    }

    @Test
    fun theTabAloneComesBack() {
        val place = TidalPlace(TidalTab.TRACKS, "", searched = false, emptyList())
        assertEquals(place, TidalPlace.fromJson(place.toJson()))
    }

    @Test
    fun otherRoutesAreLeftOut() {
        val place = TidalPlace(TidalTab.ALBUMS, "", false, listOf(Route.Settings, Route.TidalAlbum("2", "Geogaddi")))
        assertEquals(listOf(Route.TidalAlbum("2", "Geogaddi")), TidalPlace.fromJson(place.toJson())!!.pages)
    }

    @Test
    fun junkIsIgnored() {
        assertNull(TidalPlace.fromJson("not json"))
        assertEquals(TidalTab.TRACKS, TidalPlace.fromJson("""{"list":"GONE"}""")!!.list)
    }
}
