package com.maximus.tvplayer

enum class MediaKind {
    LIVE,
    MOVIE,
    SERIES,
}

data class CatalogEntry(
    val key: String,
    val name: String,
    val groupTitle: String,
    val tvgId: String,
    val logoUrl: String,
    val streamUrl: String,
    val kind: MediaKind,
    val quality: String,
    val seriesGroup: String = "",
    val season: String = "",
    val episode: String = "",
)

data class CatalogSnapshot(
    val entries: List<CatalogEntry>,
    val loadedFromCache: Boolean = false,
) {
    val live: List<CatalogEntry> get() = entries.filter { it.kind == MediaKind.LIVE }
    val movies: List<CatalogEntry> get() = entries.filter { it.kind == MediaKind.MOVIE }
    val series: List<CatalogEntry> get() = entries.filter { it.kind == MediaKind.SERIES }
    val groups: List<String> get() = entries.map { it.groupTitle.ifBlank { "Sem categoria" } }.distinct().sorted()
}
