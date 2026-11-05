package com.tlisonbee.segue.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The MediaSync server's GET /api/v1/music (server 1.5.0 and later), as far as Segue reads it. The
 * server only ever adds to its API, so unknown fields are ignored. The server's side is
 * MusicListing in MediaSync's shared/api/Api.kt.
 */

val ServerJson = Json { ignoreUnknownKeys = true }

/** The server's music: every live track at revision [rev]; [unchanged] (and no tracks) if it's the revision asked about. */
@Serializable
data class MusicListing(val rev: Long, val tracks: List<ServerTrack> = emptyList(), val unchanged: Boolean = false)

/** One track. [path] is its repository path without the extension ("music/Artist/Album/01 Title"). */
@Serializable
data class ServerTrack(
    val assetId: String,
    val deleted: Boolean = false,
    val path: String,
    /** The original first. */
    val renditions: List<ServerRendition>,
    /** By MediaSync's tag names (TagField). */
    val tags: Map<String, List<String>> = emptyMap(),
    val pictures: List<ServerPicture> = emptyList(),
)

/** One encoding of a track: [blob] is what's streamed, [format] its file extension. */
@Serializable
data class ServerRendition(val mediaHash: String = "", val blob: String, val format: String, val size: Long, val durationMs: Long? = null)

/** A picture of a track; type "front" is its cover. */
@Serializable
data class ServerPicture(val type: String, val blob: String, val mime: String = "")

/** Where music is in the server's repository. */
const val MUSIC_PREFIX = "music/"
const val FRONT_COVER = "front"
