package com.tlisonbee.segue.tidal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class TidalPagingTest {
    /** A list of [size] items, paged by 20 with offset cursors as TIDAL's favorites are, or opaque ones. */
    private class FakeList(var size: Int, val offsets: Boolean = true) {
        val asked: MutableList<String?> = Collections.synchronizedList(mutableListOf())
        val inFlight = AtomicInteger()
        var most = 0

        fun cursorFor(offset: Int) = if (offsets) offset.toString() else "c$offset"
        fun offsetOf(cursor: String?) = cursor?.removePrefix("c")?.toInt() ?: 0

        suspend fun fetch(cursor: String?): JsonObject {
            asked += cursor
            val n = inFlight.incrementAndGet()
            synchronized(this) { most = maxOf(most, n) }
            delay(5)
            inFlight.decrementAndGet()
            val from = offsetOf(cursor)
            val to = minOf(from + 20, size)
            return buildJsonObject {
                put("data", JsonArray((from until to).map { buildJsonObject { put("id", JsonPrimitive(it.toString())) } }))
                put("links", buildJsonObject { put("meta", buildJsonObject { if (to < size) put("nextCursor", JsonPrimitive(cursorFor(to))) }) })
            }
        }
    }

    private fun ids(pages: List<JsonObject>) = pages.flatMap { (it["data"] as JsonArray).map { e -> (e as JsonObject)["id"]!!.jsonPrimitive.content.toInt() } }

    private fun all(list: FakeList, count: Int?) = runBlocking {
        withContext(Dispatchers.IO) { TidalPaging.all(count, { list.fetch(it) }) }
    }

    @Test
    fun offsetCursorsAreFetchedSeveralAtATime() {
        val list = FakeList(1500)
        val pages = all(list, 1500)
        assertEquals((0 until 1500).toList(), ids(pages))
        assertEquals(75, list.asked.size)
        assertTrue("at most ${TidalPaging.PARALLEL} at once, was ${list.most}", list.most in 2..TidalPaging.PARALLEL)
    }

    @Test
    fun opaqueCursorsAreFetchedInOrder() {
        val list = FakeList(130, offsets = false)
        val pages = all(list, 130)
        assertEquals((0 until 130).toList(), ids(pages))
        assertEquals(1, list.most)
    }

    @Test
    fun withoutACountPagesAreFetchedInOrder() {
        val list = FakeList(130)
        assertEquals((0 until 130).toList(), ids(all(list, null)))
        assertEquals(1, list.most)
    }

    @Test
    fun aListLongerThanSaidIsFetchedToTheEnd() {
        val list = FakeList(130)
        assertEquals((0 until 130).toList(), ids(all(list, 100)))
    }

    @Test
    fun aListShorterThanSaidStopsAtItsEnd() {
        val list = FakeList(90)
        assertEquals((0 until 90).toList(), ids(all(list, 200)))
    }

    @Test
    fun aPageThatDoesNotFitIsFetchedAgainInOrder() {
        // Pages at offsets other than multiples of 20 (as if items moved): the page after 40 points to 55.
        val list = object {
            val inner = FakeList(200)
            suspend fun fetch(cursor: String?): JsonObject {
                val doc = inner.fetch(cursor)
                if (cursor == "40") {
                    return buildJsonObject {
                        put("data", JsonArray((40 until 55).map { buildJsonObject { put("id", JsonPrimitive(it.toString())) } }))
                        put("links", buildJsonObject { put("meta", buildJsonObject { put("nextCursor", JsonPrimitive("55")) }) })
                    }
                }
                return doc
            }
        }
        val pages = runBlocking { withContext(Dispatchers.IO) { TidalPaging.all(200, { list.fetch(it) }) } }
        assertEquals((0 until 200).toList(), ids(pages))
    }
}
