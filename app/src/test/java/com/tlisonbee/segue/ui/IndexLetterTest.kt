package com.tlisonbee.segue.ui

import com.tlisonbee.segue.library.sortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndexLetterTest {
    @Test
    fun oneLetter() {
        assertEquals("B", indexLetter(sortKey("The Beatles")))
        assertEquals("E", indexLetter(sortKey("Étoile")))
        assertEquals("#", indexLetter(sortKey("50 Cent")))
        assertEquals("#", indexLetter(sortKey("(What's the Story)")))
        assertNull(indexLetter(null))
        assertNull(indexLetter(""))
    }

    @Test
    fun twoLetters() {
        assertEquals("Sa", indexLetter(sortKey("Santana"), two = true))
        assertEquals("Be", indexLetter(sortKey("The Beatles"), two = true))
        assertEquals("S", indexLetter(sortKey("S Club 7"), two = true))
        assertEquals("X", indexLetter(sortKey("X"), two = true))
        assertEquals("R2", indexLetter(sortKey("R2-D2"), two = true))
        assertEquals("#", indexLetter(sortKey("10cc"), two = true))
    }
}
