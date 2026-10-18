package com.applemusicktv.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val id:         String,
    val name:       String,
    val artworkUrl: String?,
    val genreNames: List<String> = emptyList(),
    val editorialNotes: String?  = null,
) {
    fun artworkUrl(size: Int) = artworkUrl
        ?.replace("{w}", "$size")
        ?.replace("{h}", "$size")
        ?.replace("{f}", "jpg")
}
