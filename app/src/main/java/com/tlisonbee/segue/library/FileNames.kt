package com.tlisonbee.segue.library

import java.text.Normalizer

/** Safe file names, for playlists Segue writes: the same rules as MediaSync's, so its files sync anywhere. */
object FileNames {
    private val UNSAFE = Regex("""[<>:"/\\|?*\u0000-\u001F\u007F]""")
    private val RESERVED = Regex("""(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?""")

    /** [text] as one file name: no separators or characters Windows refuses, at most [maxBytes] bytes of UTF-8. */
    fun segment(text: String, maxBytes: Int = 255): String {
        var s = Normalizer.normalize(text, Normalizer.Form.NFC).replace(UNSAFE, "_").trim()
        s = truncateUtf8(s, maxBytes).trimEnd('.', ' ')
        if (s.isEmpty() || s == "." || s == "..") s = "_"
        if (RESERVED.matches(s)) s = "${s}_"
        return s
    }

    private fun truncateUtf8(s: String, maxBytes: Int): String {
        if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + n > maxBytes) break
            out.appendCodePoint(cp)
            bytes += n
            i += Character.charCount(cp)
        }
        return out.toString()
    }
}
