package com.maximus.tvplayer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CatalogDatabase(context: Context) {
    data class Stats(val total: Int, val live: Int, val movies: Int, val series: Int, val groups: Int)

    private val helper = Helper(context.applicationContext)

    fun replaceStreaming(feed: ((CatalogEntry) -> Unit) -> Unit, onProgress: (Int) -> Unit = {}): Stats {
        val db = helper.writableDatabase
        var total = 0
        db.beginTransaction()
        try {
            db.delete(TABLE, null, null)
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO $TABLE " +
                    "(item_key,name,group_title,tvg_id,logo_url,stream_url,kind,quality,series_group,season,episode,year,synopsis,cast,backdrop_url,trailer_url,runtime) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            feed { entry ->
                statement.clearBindings()
                bind(statement, entry)
                statement.executeInsert()
                total++
                if (total % 5_000 == 0) onProgress((60 + total / 10_000).coerceAtMost(95))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return Stats(total, countKind(db, MediaKind.LIVE), countKind(db, MediaKind.MOVIE), countKind(db, MediaKind.SERIES), countGroups(db))
    }

    fun replace(entries: Sequence<CatalogEntry>): Stats = replaceStreaming({ emit -> entries.forEach(emit) })

    fun clear() {
        helper.writableDatabase.delete(TABLE, null, null)
    }

    fun count(): Int = helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun stats(): Stats {
        val db = helper.readableDatabase
        return Stats(count(), countKind(db, MediaKind.LIVE), countKind(db, MediaKind.MOVIE), countKind(db, MediaKind.SERIES), countGroups(db))
    }

    fun groups(kind: MediaKind, hidden: Set<String>): List<String> {
        val db = helper.readableDatabase
        val args = mutableListOf(kind.name)
        val hiddenClause = hiddenClause(hidden, args)
        val sql = "SELECT DISTINCT group_title FROM $TABLE WHERE kind=? $hiddenClause ORDER BY group_title COLLATE NOCASE"
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0).orEmpty().ifBlank { "Sem categoria" })
            }
        }
    }

    fun queryPage(
        kind: MediaKind?,
        group: String,
        search: String,
        hidden: Set<String>,
        favorites: Set<String>,
        sortAlphabetically: Boolean,
        limit: Int,
        offset: Int,
        seriesOnly: Boolean = false,
    ): List<CatalogEntry> {
        if (seriesOnly) return querySeriesPage(group, search, hidden, sortAlphabetically, limit, offset)
        if (kind == null && favorites.isEmpty()) return emptyList()
        val db = helper.readableDatabase
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        kind?.let { where += "kind=?"; args += it.name }
        if (group != "Todos") { where += "group_title=?"; args += group }
        if (search.isNotBlank()) {
            where += "(LOWER(name) LIKE ? OR LOWER(group_title) LIKE ? OR LOWER(tvg_id) LIKE ?)"
            val value = "%${search.trim().lowercase()}%"
            args += value; args += value; args += value
        }
        if (hidden.isNotEmpty()) where += "UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})".also { args.addAll(hidden.map(String::uppercase)) }
        if (favorites.isNotEmpty()) where += "item_key IN (${favorites.joinToString(",") { "?" }})".also { args.addAll(favorites) }
        val selection = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        val order = if (sortAlphabetically) "name COLLATE NOCASE ASC" else "rowid ASC"
        val sql = "SELECT * FROM $TABLE $selection ORDER BY $order LIMIT $limit OFFSET $offset"
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(readEntry(cursor))
            }
        }
    }

    private fun querySeriesPage(group: String, search: String, hidden: Set<String>, sortAlphabetically: Boolean, limit: Int, offset: Int): List<CatalogEntry> {
        val db = helper.readableDatabase
        val (filter, baseArgs) = seriesFilter("item", group, search, hidden)
        val identity = "CASE WHEN TRIM(item.series_group) <> '' THEN item.series_group ELSE item.name END"
        val identityOrder = if (sortAlphabetically) "series_identity COLLATE NOCASE ASC" else "MIN(source_rowid) ASC"
        val identitySql = "SELECT series_identity FROM (" +
            "SELECT $identity AS series_identity, item.rowid AS source_rowid FROM $TABLE item WHERE $filter" +
            ") GROUP BY series_identity ORDER BY $identityOrder LIMIT $limit OFFSET $offset"
        val identities = db.rawQuery(identitySql, baseArgs.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
        return identities.mapNotNull { seriesIdentity ->
            val detailFilter = "$filter AND $identity=?"
            val detailArgs = (baseArgs + seriesIdentity).toTypedArray()
            db.rawQuery(
                "SELECT * FROM $TABLE item WHERE $detailFilter " +
                    "ORDER BY COALESCE(CAST(NULLIF(item.season, '') AS INTEGER), 1), " +
                    "COALESCE(CAST(NULLIF(item.episode, '') AS INTEGER), 0), item.rowid LIMIT 1",
                detailArgs,
            ).use { cursor -> if (cursor.moveToFirst()) readEntry(cursor) else null }
        }
    }

    private fun seriesFilter(alias: String, group: String, search: String, hidden: Set<String>): Pair<String, List<String>> {
        val where = mutableListOf("$alias.kind=?")
        val args = mutableListOf(MediaKind.SERIES.name)
        if (group != "Todos") { where += "$alias.group_title=?"; args += group }
        if (search.isNotBlank()) {
            where += "(LOWER($alias.name) LIKE ? OR LOWER($alias.group_title) LIKE ? OR LOWER($alias.tvg_id) LIKE ? OR LOWER($alias.series_group) LIKE ?)"
            val value = "%${search.trim().lowercase()}%"
            args += value; args += value; args += value; args += value
        }
        if (hidden.isNotEmpty()) where += "UPPER($alias.group_title) NOT IN (${hidden.joinToString(",") { "?" }})".also { args.addAll(hidden.map(String::uppercase)) }
        return where.joinToString(" AND ") to args
    }

    fun first(kind: MediaKind?, group: String, search: String, hidden: Set<String>, favorites: Set<String>, sortAlphabetically: Boolean): CatalogEntry? =
        queryPage(kind, group, search, hidden, favorites, sortAlphabetically, 1, 0).firstOrNull()

    fun querySeriesSeasons(seriesGroup: String, group: String, hidden: Set<String>): List<String> {
        val db = helper.readableDatabase
        val where = mutableListOf("kind=?", "series_group=?")
        val args = mutableListOf(MediaKind.SERIES.name, seriesGroup)
        if (group != "Todos") { where += "group_title=?"; args += group }
        if (hidden.isNotEmpty()) where += "UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})".also { args.addAll(hidden.map(String::uppercase)) }
        val seasonExpr = "CASE WHEN TRIM(season) = '' THEN '1' ELSE season END"
        val sql = "SELECT DISTINCT $seasonExpr AS season_value FROM $TABLE WHERE ${where.joinToString(" AND ")} ORDER BY CAST($seasonExpr AS INTEGER), season_value"
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.getString(0).orEmpty().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    fun querySeriesEpisodes(seriesGroup: String, season: String, group: String, hidden: Set<String>): List<CatalogEntry> {
        val db = helper.readableDatabase
        val where = mutableListOf("kind=?", "series_group=?")
        val args = mutableListOf(MediaKind.SERIES.name, seriesGroup)
        val seasonExpr = "CASE WHEN TRIM(season) = '' THEN '1' ELSE season END"
        where += "$seasonExpr=?"
        args += season
        if (group != "Todos") { where += "group_title=?"; args += group }
        if (hidden.isNotEmpty()) where += "UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})".also { args.addAll(hidden.map(String::uppercase)) }
        val sql = "SELECT * FROM $TABLE WHERE ${where.joinToString(" AND ")} ORDER BY CAST(NULLIF(episode, '') AS INTEGER), name COLLATE NOCASE"
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(readEntry(cursor))
            }
        }
    }

    fun close() = helper.close()

    private fun hiddenClause(hidden: Set<String>, args: MutableList<String>): String {
        if (hidden.isEmpty()) return ""
        args.addAll(hidden.map(String::uppercase))
        return "AND UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})"
    }

    private fun countKind(db: SQLiteDatabase, kind: MediaKind): Int = db.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE kind=?", arrayOf(kind.name)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    private fun countGroups(db: SQLiteDatabase): Int = db.rawQuery("SELECT COUNT(DISTINCT group_title) FROM $TABLE", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun bind(statement: android.database.sqlite.SQLiteStatement, e: CatalogEntry) {
        listOf(e.key, e.name, e.groupTitle, e.tvgId, e.logoUrl, e.streamUrl, e.kind.name, e.quality, e.seriesGroup, e.season, e.episode, e.year, e.synopsis, e.cast, e.backdropUrl, e.trailerUrl, e.runtime)
            .forEachIndexed { index, value -> statement.bindString(index + 1, value) }
    }

    private fun readEntry(c: android.database.Cursor): CatalogEntry = CatalogEntry(
        key = c.getString(c.getColumnIndexOrThrow("item_key")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        groupTitle = c.getString(c.getColumnIndexOrThrow("group_title")),
        tvgId = c.getString(c.getColumnIndexOrThrow("tvg_id")),
        logoUrl = c.getString(c.getColumnIndexOrThrow("logo_url")),
        streamUrl = c.getString(c.getColumnIndexOrThrow("stream_url")),
        kind = MediaKind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),
        quality = c.getString(c.getColumnIndexOrThrow("quality")),
        seriesGroup = c.getString(c.getColumnIndexOrThrow("series_group")),
        season = c.getString(c.getColumnIndexOrThrow("season")),
        episode = c.getString(c.getColumnIndexOrThrow("episode")),
        year = c.getString(c.getColumnIndexOrThrow("year")),
        synopsis = c.getString(c.getColumnIndexOrThrow("synopsis")),
        cast = c.getString(c.getColumnIndexOrThrow("cast")),
        backdropUrl = c.getString(c.getColumnIndexOrThrow("backdrop_url")),
        trailerUrl = c.getString(c.getColumnIndexOrThrow("trailer_url")),
        runtime = c.getString(c.getColumnIndexOrThrow("runtime")),
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, "excellence_catalog.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $TABLE (item_key TEXT PRIMARY KEY, name TEXT NOT NULL, group_title TEXT NOT NULL, tvg_id TEXT, logo_url TEXT, stream_url TEXT NOT NULL, kind TEXT NOT NULL, quality TEXT, series_group TEXT, season TEXT, episode TEXT, year TEXT, synopsis TEXT, cast TEXT, backdrop_url TEXT, trailer_url TEXT, runtime TEXT)")
            db.execSQL("CREATE INDEX idx_catalog_kind_group ON $TABLE(kind, group_title)")
            db.execSQL("CREATE INDEX idx_catalog_name ON $TABLE(name COLLATE NOCASE)")
            db.execSQL("CREATE INDEX idx_catalog_series_season ON $TABLE(kind, series_group, season)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_series_season ON $TABLE(kind, series_group, season)")
        }
    }

    private companion object { const val TABLE = "catalog_items" }
}
