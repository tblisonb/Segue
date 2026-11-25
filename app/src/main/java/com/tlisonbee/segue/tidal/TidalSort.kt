package com.tlisonbee.segue.tidal

import com.tlisonbee.segue.library.sortKey

/** An order for a TIDAL list of tracks (a playlist, your favorites); albums keep theirs. */
data class TidalSort(val by: By = By.LIST, val reversed: Boolean = false) {
    enum class By(val label: String) {
        /** As TIDAL lists it: a playlist's own order; favorites newest first. */
        LIST("Playlist order"),

        /** Newest first; reversed, oldest first. */
        ADDED("Recently added"),
        TITLE("Title"),
        ARTIST("Artist"),
        ALBUM("Album"),
    }

    /** [items] in this order; the sort is stable, so ties keep the list's order. */
    fun apply(items: List<TidalItem>): List<TidalItem> {
        val sorted = when (by) {
            By.LIST -> items
            // Entries without a date (none are expected) go last.
            By.ADDED -> items.sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
            By.TITLE -> items.sortedWith(compareBy({ it.track?.titleKey }, { it.track?.artistKey }))
            By.ARTIST -> items.sortedWith(compareBy({ it.track?.artistKey }, { it.track?.albumTitleKey }, { it.track?.discNumber }, { it.track?.trackNumber }))
            By.ALBUM -> items.sortedWith(compareBy({ it.track?.albumTitleKey }, { it.track?.discNumber }, { it.track?.trackNumber }))
        }
        return if (reversed) sorted.asReversed() else sorted
    }
}

/**
 * An order for your TIDAL albums, artists and playlists: TIDAL's own (favorites newest first,
 * playlists most recently changed first) or by name. One choice for all three, as [TidalSort] is
 * for track lists.
 */
data class TidalNameSort(val byName: Boolean = false, val reversed: Boolean = false) {
    /** [items] in this order; by name as the library sorts ("The" ignored), then by artist. */
    fun apply(items: List<TidalItem>): List<TidalItem> {
        val sorted = if (byName) items.sortedWith(compareBy({ sortKey(it.title) }, { sortKey(it.artist) })) else items
        return if (reversed) sorted.asReversed() else sorted
    }
}
