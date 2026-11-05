package com.tlisonbee.segue.tags

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagField as JTagField
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Tag field names: the same ones MediaSync's server uses for a track's tags. */
object TagField {
    const val TITLE = "title"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val ALBUM_ARTIST = "albumartist"
    const val DATE = "date"
    const val GENRE = "genre"
    const val TRACK_NUMBER = "tracknumber"
    const val DISC_NUMBER = "discnumber"

    // Loudness, as ReplayGain 2.0 writes it ("-6.02 dB", peaks as "0.988000").
    const val REPLAYGAIN_TRACK_GAIN = "replaygain_track_gain"
    const val REPLAYGAIN_TRACK_PEAK = "replaygain_track_peak"
    const val REPLAYGAIN_ALBUM_GAIN = "replaygain_album_gain"
    const val REPLAYGAIN_ALBUM_PEAK = "replaygain_album_peak"
    val REPLAYGAIN = listOf(REPLAYGAIN_TRACK_GAIN, REPLAYGAIN_TRACK_PEAK, REPLAYGAIN_ALBUM_GAIN, REPLAYGAIN_ALBUM_PEAK)
}

/** Reads an audio file's tags, cover and length with jaudiotagger. Read-only: Segue never changes your files' tags. */
object TrackTags {
    private val keys = mapOf(
        TagField.TITLE to FieldKey.TITLE,
        TagField.ARTIST to FieldKey.ARTIST,
        TagField.ALBUM to FieldKey.ALBUM,
        TagField.ALBUM_ARTIST to FieldKey.ALBUM_ARTIST,
        TagField.DATE to FieldKey.YEAR,
        TagField.GENRE to FieldKey.GENRE,
        TagField.TRACK_NUMBER to FieldKey.TRACK,
        TagField.DISC_NUMBER to FieldKey.DISC_NO,
    )

    // jaudiotagger logs routine events at every level (up to SEVERE), so its log is useless;
    // failures still arrive as exceptions. Held here because java.util.logging only keeps weak
    // references to loggers, and a collected one would forget its level.
    @Suppress("unused")
    private val jaudiotaggerLog = Logger.getLogger("org.jaudiotagger").apply { level = Level.OFF }

    /** Android has no java.awt or javax.imageio; jaudiotagger must avoid them. */
    fun useAndroidMode() {
        TagOptionSingleton.getInstance().isAndroid = true
    }

    class Inspection(
        /** By [TagField] name; the ReplayGain values are in [replayGain]. */
        val tags: Map<String, List<String>>,
        /** The front cover (or else the first picture), as stored. */
        val cover: ByteArray?,
        val durationMs: Long?,
        /**
         * The ReplayGain tags, by their names in files (upper case, as in [TagField.REPLAYGAIN]) as
         * written (`-6.02 dB`): `TXXX` frames in MP3, comments in FLAC and Ogg, iTunes fields in M4A.
         */
        val replayGain: Map<String, String> = emptyMap(),
    ) {
        operator fun get(field: String): List<String> = tags[field].orEmpty()
    }

    private val REPLAYGAIN = TagField.REPLAYGAIN.map { it.uppercase() }

    /** A field's free-form name and text, for the formats that let a tag name its own fields. */
    private fun namedText(field: JTagField): Pair<String, String>? = when {
        field is AbstractID3v2Frame -> (field.body as? FrameBodyTXXX)?.let { it.description to it.textWithoutTrailingNulls }
        field is Mp4TagReverseDnsField -> field.descriptor to field.content
        field is TagTextField -> field.id to field.content
        else -> null
    }

    fun inspect(file: File): Inspection {
        val audio = AudioFileIO.read(file)
        val duration = runCatching { (audio.audioHeader.preciseTrackLength * 1000).toLong() }.getOrNull()?.takeIf { it > 0 }
        val tag = audio.tag ?: return Inspection(emptyMap(), null, duration)
        val fields = keys.mapNotNull { (name, key) ->
            val values = tag.getAll(key).filter { it.isNotBlank() }
            if (values.isEmpty()) null else name to values
        }.toMap()
        val cover = tag.artworkList.firstOrNull { it.pictureType == PictureTypes.DEFAULT_ID } ?: tag.firstArtwork
        val replayGain = buildMap {
            for (field in runCatching { tag.fields.asSequence().toList() }.getOrDefault(emptyList())) {
                val (name, text) = namedText(field) ?: continue
                val key = name.uppercase()
                if (key in REPLAYGAIN && text.isNotBlank()) putIfAbsent(key, text.trim())
            }
        }
        return Inspection(fields, cover?.binaryData, duration, replayGain)
    }
}
