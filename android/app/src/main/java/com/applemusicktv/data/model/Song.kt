package com.applemusicktv.data.model

import androidx.compose.runtime.Immutable

/**
 * A queue / track-list row. `@Immutable` keeps the Compose compiler from treating it as unstable
 * (it holds a `List<String>`), which is what lets long track lists skip re-composing every row on
 * each focus change or state tick.
 */
@Immutable
data class Song(
    val id:             String,
    val title:          String,
    val artistName:     String,
    val albumName:      String,
    val durationMs:     Long,
    val artworkUrl:     String?,
    val artworkBgColor: String?,
    val previewUrl:     String?,
    val hasLyrics:      Boolean = false,
    val trackNumber:    Int?    = null,
    val genreNames:     List<String> = emptyList(),
    val artistId:       String? = null,
    val albumId:        String? = null,
    val type:           String  = "songs",
) {
    /** Music-video rows (catalog `music-videos` / library `library-music-videos`)
     *  play through the fullscreen video path, not the audio player. */
    val isMusicVideo: Boolean get() = type.contains("music-video")

    fun artworkUrl(size: Int) = artworkUrl
        ?.replace("{w}", "$size")
        ?.replace("{h}", "$size")
        ?.replace("{f}", "jpg")

    /**
     * "m:ss" — computed once. `String.format` costs ~10 µs and this is read by every visible row on
     * every recomposition; a 300-track list was spending real time on it while scrolling.
     * `lazy` keeps it out of equals/hashCode/copy, so the data class semantics are unchanged.
     */
    val durationFormatted: String by lazy {
        val s = durationMs / 1000
        val m = s / 60
        val sec = s % 60
        if (sec < 10) "$m:0$sec" else "$m:$sec"
    }
}
