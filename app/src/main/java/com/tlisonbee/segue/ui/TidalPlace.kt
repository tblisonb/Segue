package com.tlisonbee.segue.ui

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Where you were in TIDAL when Segue last closed, so it opens there again: the TIDAL tab (which
 * of its lists, and a search), or the TIDAL albums, artists and playlists you'd opened from it.
 * Leaving TIDAL forgets it, and Segue then opens as usual.
 */
data class TidalPlace(
    val list: TidalTab,
    /** What was searched for (run again on opening), or else what was typed in the search box. */
    val query: String,
    val searched: Boolean,
    /** The TIDAL pages open, oldest first; empty for the tab itself. */
    val pages: List<Route>,
) {
    fun toJson(): String = buildJsonObject {
        put("list", list.name)
        put("query", query)
        put("searched", searched)
        put(
            "pages",
            buildJsonArray {
                pages.forEach { r ->
                    add(
                        when (r) {
                            is Route.TidalAlbum -> page("album", r.id, r.title)
                            is Route.TidalPlaylist -> page("playlist", r.id, r.title)
                            is Route.TidalArtist -> page("artist", r.id, r.name)
                            else -> return@forEach
                        },
                    )
                }
            },
        )
    }.toString()

    companion object {
        private const val KEY = "tidal_place"

        private fun page(kind: String, id: String, title: String) = buildJsonObject {
            put("kind", kind)
            put("id", id)
            put("title", title)
        }

        fun isTidal(route: Route) = route is Route.TidalAlbum || route is Route.TidalPlaylist || route is Route.TidalArtist

        /** Where you are now, if it's in TIDAL. */
        fun of(stack: List<Route>, tab: HomeTab, browser: TidalBrowser): TidalPlace? {
            val top = stack.lastOrNull() ?: return null
            if (!isTidal(top) && !(top == Route.Home && tab == HomeTab.TIDAL)) return null
            val searched = browser.searchedFor
            return TidalPlace(browser.tab, searched ?: browser.query, searched != null, stack.filter(::isTidal))
        }

        fun fromJson(text: String): TidalPlace? = runCatching {
            val json = Json.parseToJsonElement(text) as JsonObject
            TidalPlace(
                list = runCatching { TidalTab.valueOf(json["list"]!!.jsonPrimitive.content) }.getOrDefault(TidalTab.TRACKS),
                query = (json["query"] as? JsonPrimitive)?.content.orEmpty(),
                searched = (json["searched"] as? JsonPrimitive)?.booleanOrNull == true,
                pages = (json["pages"] as? JsonArray).orEmpty().mapNotNull { e ->
                    val p = e as? JsonObject ?: return@mapNotNull null
                    val id = (p["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val title = (p["title"] as? JsonPrimitive)?.content.orEmpty()
                    when ((p["kind"] as? JsonPrimitive)?.content) {
                        "album" -> Route.TidalAlbum(id, title)
                        "playlist" -> Route.TidalPlaylist(id, title)
                        "artist" -> Route.TidalArtist(id, title)
                        else -> null
                    }
                },
            )
        }.getOrNull()

        fun save(prefs: SharedPreferences, place: TidalPlace?) {
            prefs.edit().apply { if (place == null) remove(KEY) else putString(KEY, place.toJson()) }.apply()
        }

        fun load(prefs: SharedPreferences): TidalPlace? = prefs.getString(KEY, null)?.let(::fromJson)
    }
}
