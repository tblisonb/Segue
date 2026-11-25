package com.tlisonbee.segue.tidal

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Fetching every page of a TIDAL list. TIDAL pages lists by 20 and has no parameter to ask for
 * more, and a page takes about half a second, so 1500 tracks one page after another take 30 s or
 * more. But the cursors seen so far are plain offsets ("20", "40", …), so when the first page's
 * cursor is one and the list's size is known, the other pages are fetched [parallel] at a time.
 * Every page is checked against the one before it (its cursor must be where that one pointed);
 * at the first that doesn't fit, or if the list turns out longer, the rest are fetched one after
 * another the ordinary way.
 */
object TidalPaging {
    const val PARALLEL = 4

    fun nextCursor(doc: JsonObject): String? = doc.obj("links")?.obj("meta")?.string("nextCursor")?.takeIf { it.isNotEmpty() }

    fun size(doc: JsonObject): Int = (doc["data"] as? JsonArray)?.size ?: 0

    /**
     * Every page, in order. [count] is how many items TIDAL says the list has (null if unknown);
     * [fetch] gets one page (null for the first); [onProgress] sees the pages so far, in order.
     */
    suspend fun all(
        count: Int?,
        fetch: suspend (cursor: String?) -> JsonObject,
        parallel: Int = PARALLEL,
        onProgress: (List<JsonObject>) -> Unit = {},
    ): List<JsonObject> {
        val docs = mutableListOf(fetch(null))
        onProgress(docs.toList())
        val step = size(docs[0])
        if (parallel > 1 && count != null && step > 0 && nextCursor(docs[0]) == step.toString()) {
            val offsets = generateSequence(step) { it + step }.takeWhile { it < count }.toList()
            val permits = Semaphore(parallel)
            coroutineScope {
                val pages = offsets.map { offset -> async { permits.withPermit { fetch(offset.toString()) } } }
                for ((i, page) in pages.withIndex()) {
                    // Not where the page before pointed (the list changed meanwhile, or this list's
                    // cursors aren't offsets after all): the rest are fetched in order below.
                    if (nextCursor(docs.last()) != offsets[i].toString()) {
                        pages.drop(i).forEach { it.cancel() }
                        break
                    }
                    docs += page.await()
                    onProgress(docs.toList())
                }
            }
        }
        var cursor = nextCursor(docs.last())
        while (cursor != null) {
            val doc = fetch(cursor)
            docs += doc
            onProgress(docs.toList())
            cursor = nextCursor(doc)
        }
        return docs
    }
}
