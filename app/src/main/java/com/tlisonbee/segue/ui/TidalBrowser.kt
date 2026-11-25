package com.tlisonbee.segue.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tlisonbee.segue.tidal.Tidal
import com.tlisonbee.segue.tidal.TidalItem
import com.tlisonbee.segue.tidal.TidalCatalog
import com.tlisonbee.segue.tidal.TidalList
import com.tlisonbee.segue.tidal.TidalPaging
import com.tlisonbee.segue.tidal.TidalSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One TIDAL list as the screens show it. [items] stay what they were while an update runs (so a
 * cached playlist still plays in full); only a list seen for the first time grows page by page.
 */
data class TidalListState(
    val items: List<TidalItem> = emptyList(),
    /** Fetching from TIDAL: the list for the first time, or an update. */
    val loading: Boolean = false,
    val error: String? = null,
    val loadedAt: Long = 0,
    /** TIDAL has a newer version than the copy shown; it's fetched only when you ask ([TidalBrowser.update]). */
    val changed: Boolean = false,
    /** While fetching: items fetched so far, and how many TIDAL says there are. */
    val fetched: Int = 0,
    val total: Int? = null,
)

/** Which of your TIDAL lists the TIDAL tab shows. */
enum class TidalTab(val label: String, val list: TidalList) {
    TRACKS("Tracks", TidalList.FAVORITE_TRACKS),
    ALBUMS("Albums", TidalList.FAVORITE_ALBUMS),
    ARTISTS("Artists", TidalList.FAVORITE_ARTISTS),
    PLAYLISTS("Playlists", TidalList.PLAYLISTS),
}

/**
 * The TIDAL tab's state: its lists and search. A list is fetched from TIDAL the first time it's
 * shown; after that its cached copy is what shows, and it's fetched again only when you ask
 * ([update]) or after an edit Segue made to it. Showing it just asks TIDAL (one small request)
 * whether it changed, to say so.
 */
class TidalBrowser(private val scope: CoroutineScope, val tidal: Tidal) {
    var tab by mutableStateOf(TidalTab.TRACKS)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var search by mutableStateOf<TidalSearch?>(null)
    var searchError by mutableStateOf<String?>(null)
    private var searchJob: Job? = null

    private val lists = mutableStateMapOf<String, TidalListState>()

    private fun key(list: TidalList, id: String?) = "$list/${id.orEmpty()}"

    fun state(list: TidalList, id: String? = null): TidalListState = lists[key(list, id)] ?: TidalListState()

    /** Lists Segue itself changed on TIDAL: fetched again when next shown. */
    private val edited = mutableSetOf<String>()

    /** Which version of each list is on screen, and when it was fetched. */
    private class Shown(val version: String?, val fetched: Long)
    private val shown = mutableMapOf<String, Shown>()

    /**
     * Shows a list: from memory, else from the disk cache at once, else fetched from TIDAL. A
     * cached list isn't fetched again here (but see [edited]); unless it was checked in the last
     * minute, TIDAL is asked whether it changed, which [TidalListState.changed] then says.
     */
    fun load(list: TidalList, id: String? = null) {
        val k = key(list, id)
        val now = lists[k]
        if (k in edited) return update(list, id)
        if (now != null && (now.loading || (now.error == null && System.currentTimeMillis() - now.loadedAt < CHECK_MS))) return
        val showing = !now?.items.isNullOrEmpty()
        lists[k] = (now ?: TidalListState()).copy(loading = !showing, error = null)
        scope.launch {
            try {
                if (!showing) {
                    val cached = withContext(Dispatchers.IO) { tidal.cache.read(list, id) }
                        ?: return@launch fetch(list, id, k, null)
                    lists[k] = TidalListState(cached.items)
                    shown[k] = Shown(cached.version, cached.fetched)
                }
                val copy = shown[k]
                val changed = when (list) {
                    TidalList.ALBUM_TRACKS -> false
                    // No date to go by: an artist's albums (a page or two) are simply fetched again after a day.
                    TidalList.ARTIST_ALBUMS -> {
                        if ((copy?.fetched ?: 0) < System.currentTimeMillis() - DAY_MS) return@launch fetch(list, id, k, null)
                        false
                    }
                    // A failed check says nothing either way: the copy shown stays, and isn't marked changed.
                    else -> withContext(Dispatchers.IO) { runCatching { tidal.catalog.version(list, id) }.getOrNull() }
                        ?.let { it.key != copy?.version } ?: false
                }
                lists[k] = (lists[k] ?: TidalListState()).copy(loading = false, changed = changed, loadedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                lists[k] = (lists[k] ?: TidalListState()).copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Fetches a list from TIDAL again, now: the "Update from TIDAL" button. */
    fun update(list: TidalList, id: String? = null) {
        val k = key(list, id)
        if (lists[k]?.loading == true) return
        edited -= k
        lists[k] = (lists[k] ?: TidalListState()).copy(loading = true, error = null, fetched = 0, total = null)
        scope.launch {
            try {
                val version = withContext(Dispatchers.IO) { runCatching { tidal.catalog.version(list, id) }.getOrNull() }
                fetch(list, id, k, version)
            } catch (e: Exception) {
                lists[k] = (lists[k] ?: TidalListState()).copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Every page of a list, then into the cache; what's on screen stays until it's all there. */
    private suspend fun fetch(list: TidalList, id: String?, k: String, known: TidalCatalog.ListVersion?) {
        try {
            val version = known ?: withContext(Dispatchers.IO) { runCatching { tidal.catalog.version(list, id) }.getOrNull() }
            val growing = lists[k]?.items.isNullOrEmpty()
            lists[k] = (lists[k] ?: TidalListState()).copy(loading = true, error = null, fetched = 0, total = version?.count)
            val entry = withContext(Dispatchers.IO) {
                val pages = tidal.catalog.allDocuments(list, id, version?.count) { sofar ->
                    val fetched = sofar.sumOf { TidalPaging.size(it) }
                    // Parsing every page so far each time is only worth it while there's nothing else to show.
                    val shown = if (growing) sofar.flatMap { TidalCatalog.parsePage(it).items } else null
                    // Only while still fetching: a late one mustn't replace the finished list.
                    scope.launch { lists[k]?.takeIf { it.loading }?.let { lists[k] = it.copy(items = shown ?: it.items, fetched = fetched) } }
                }
                tidal.cache.write(list, id, version?.key, pages)
            }
            lists[k] = TidalListState(entry.items, loadedAt = System.currentTimeMillis())
            shown[k] = Shown(entry.version, entry.fetched)
        } catch (e: Exception) {
            lists[k] = (lists[k] ?: TidalListState()).copy(loading = false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    /** After an edit Segue made: the list is fetched again when next shown. */
    fun invalidate(list: TidalList, id: String? = null) {
        val k = key(list, id)
        edited += k
        lists[k]?.let { lists[k] = it.copy(loadedAt = 0) }
    }

    /** Takes entries out of a list on screen at once, before it's fetched again. */
    fun drop(list: TidalList, id: String?, gone: (TidalItem) -> Boolean) {
        val k = key(list, id)
        edited += k
        lists[k]?.let { lists[k] = it.copy(items = it.items.filterNot(gone), loadedAt = 0) }
    }

    /** The IDs of your favorite tracks, as far as they've been loaded. */
    fun favoriteIds(kind: TidalItem.Kind): Set<String> {
        val list = when (kind) {
            TidalItem.Kind.TRACK -> TidalList.FAVORITE_TRACKS
            TidalItem.Kind.ALBUM -> TidalList.FAVORITE_ALBUMS
            TidalItem.Kind.ARTIST -> TidalList.FAVORITE_ARTISTS
            TidalItem.Kind.PLAYLIST -> TidalList.PLAYLISTS
        }
        return state(list).items.mapTo(HashSet()) { it.id }
    }

    /** Your own playlists (the ones you can add to), as far as they've been loaded. */
    fun ownPlaylists(): List<TidalItem> = state(TidalList.PLAYLISTS).items.filter { tidal.account.user in it.owners }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) {
            search = null
            return
        }
        searchJob?.cancel()
        searching = true
        searchError = null
        searchJob = scope.launch {
            try {
                search = withContext(Dispatchers.IO) { tidal.catalog.search(q) }
            } catch (e: Exception) {
                searchError = e.message ?: e.javaClass.simpleName
            } finally {
                searching = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        query = ""
        search = null
        searchError = null
        searching = false
    }

    companion object {
        /** How long a list is trusted before TIDAL is asked whether it changed. */
        const val CHECK_MS = 60_000L
        const val DAY_MS = 24 * 3600_000L
    }
}
