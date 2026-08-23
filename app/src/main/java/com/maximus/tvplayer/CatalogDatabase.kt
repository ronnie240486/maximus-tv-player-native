package com.maximus.tvplayer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CatalogDatabase(context: Context) {
    data class Stats(val total: Int, val live: Int, val movies: Int, val series: Int, val groups: Int)

    private val helper = Helper(context.applicationContext)

    fun replaceStreaming(
        feed: ((CatalogEntry) -> Unit) -> Unit,
        onProgress: (Int) -> Unit = {},
        onCatalogReady: (Stats) -> Unit = {},
    ): Stats {
        val db = helper.writableDatabase
        var total = 0
        var liveCount = 0
        var movieCount = 0
        var seriesCount = 0
        val groups = HashSet<String>()
        var transactionOpen = false
        var readySent = false
        val batchSize = 2_000
        // O catálogo é um cache reconstruível: durante a importação, priorizamos velocidade.
        db.execSQL("PRAGMA synchronous=OFF")
        db.execSQL("PRAGMA temp_store=MEMORY")
        // Remover índices antes do DELETE evita manter três estruturas enquanto a tabela é esvaziada.
        db.execSQL("DROP INDEX IF EXISTS idx_catalog_kind_group")
        db.execSQL("DROP INDEX IF EXISTS idx_catalog_name")
        db.execSQL("DROP INDEX IF EXISTS idx_catalog_series_season")
        fun beginBatch() {
            if (!transactionOpen) {
                db.beginTransactionNonExclusive()
                transactionOpen = true
            }
        }
        fun commitBatch() {
            if (transactionOpen) {
                db.setTransactionSuccessful()
                db.endTransaction()
                transactionOpen = false
            }
        }
        try {
            beginBatch()
            db.delete(TABLE, null, null)
            val statement = db.compileStatement(
                "INSERT OR IGNORE INTO $TABLE " +
                    "(item_key,name,group_title,tvg_id,logo_url,stream_url,kind,quality,series_group,season,episode,year,synopsis,cast,backdrop_url,trailer_url,runtime,is_adult) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            feed { entry ->
                statement.clearBindings()
                bind(statement, entry)
                val insertedRowId = statement.executeInsert()
                if (insertedRowId != -1L) {
                    total++
                    when (entry.kind) {
                        MediaKind.LIVE -> liveCount++
                        MediaKind.MOVIE -> movieCount++
                        MediaKind.SERIES -> seriesCount++
                    }
                    groups += entry.groupTitle
                    if (total % 2_000 == 0) onProgress((60 + total / 8_000).coerceAtMost(94))
                    if (total % batchSize == 0) {
                        commitBatch()
                        if (!readySent && total >= batchSize) {
                            readySent = true
                            runCatching { onCatalogReady(Stats(total, liveCount, movieCount, seriesCount, groups.size)) }
                        }
                        beginBatch()
                    }
                }
            }
            commitBatch()
        } finally {
            if (transactionOpen) {
                db.endTransaction()
                transactionOpen = false
            }
            // Recriar fora da transação garante índices mesmo se o download falhar no meio.
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_kind_group ON $TABLE(kind, group_title)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_name ON $TABLE(name COLLATE NOCASE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_series_season ON $TABLE(kind, series_group, season)")
            db.execSQL("PRAGMA synchronous=NORMAL")
        }
        return Stats(total, liveCount, movieCount, seriesCount, groups.size)
    }

    fun replace(entries: Sequence<CatalogEntry>): Stats = replaceStreaming({ emit -> entries.forEach(emit) })

    fun clear() {
        helper.writableDatabase.delete(TABLE, null, null)
    }

    fun count(): Int = helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun stats(): Stats {
        return aggregateStats(helper.readableDatabase)
    }

    fun groups(kind: MediaKind, hidden: Set<String>, includeAdult: Boolean = false): List<String> {
        val db = helper.readableDatabase
        val args = mutableListOf(kind.name)
        val hiddenClause = hiddenClause(hidden, args)
        val sql = "SELECT DISTINCT group_title FROM $TABLE WHERE kind=? AND is_adult=0 $hiddenClause ORDER BY group_title COLLATE NOCASE"
        val groups = db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0).orEmpty().ifBlank { "Sem categoria" })
            }
        }.toMutableList()
        if (includeAdult) {
            val adultArgs = mutableListOf(kind.name)
            val adultHiddenClause = hiddenClause(hidden, adultArgs)
            val hasAdult = db.rawQuery("SELECT 1 FROM $TABLE WHERE kind=? AND is_adult=1 $adultHiddenClause LIMIT 1", adultArgs.toTypedArray()).use { it.moveToFirst() }
            if (hasAdult) groups += ContentSafety.LOCKED_CATEGORY
        }
        return groups
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
        includeAdult: Boolean = false,
    ): List<CatalogEntry> {
        if (seriesOnly) return querySeriesPage(group, search, hidden, sortAlphabetically, limit, offset, includeAdult)
        if (kind == null && favorites.isEmpty()) return emptyList()
        val db = helper.readableDatabase
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        kind?.let { where += "kind=?"; args += it.name }
        when {
            group == ContentSafety.LOCKED_CATEGORY -> where += if (includeAdult) "is_adult=1" else "0"
            group != "Todos" -> { where += "group_title=? AND is_adult=0"; args += group }
            !includeAdult -> where += "is_adult=0"
        }
        if (search.isNotBlank()) {
            where += "(LOWER(name) LIKE ? OR LOWER(group_title) LIKE ? OR LOWER(tvg_id) LIKE ?)"
            val value = "%${search.trim().lowercase()}%"
            args += value; args += value; args += value
        }
        if (hidden.isNotEmpty()) where += "UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})".also { args.addAll(hidden.map(String::uppercase)) }
        if (favorites.isNotEmpty()) where += "item_key IN (${favorites.joinToString(",") { "?" }})".also { args.addAll(favorites) }
        val selection = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        val order = if (sortAlphabetically) "is_adult ASC, name COLLATE NOCASE ASC" else "is_adult ASC, rowid ASC"
        val sql = "SELECT * FROM $TABLE $selection ORDER BY $order LIMIT $limit OFFSET $offset"
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(readEntry(cursor))
            }
        }
    }

    private fun querySeriesPage(group: String, search: String, hidden: Set<String>, sortAlphabetically: Boolean, limit: Int, offset: Int, includeAdult: Boolean): List<CatalogEntry> {
        val db = helper.readableDatabase
        val (sourceFilter, sourceArgs) = seriesFilter("source", group, search, hidden, includeAdult)
        val sourceIdentity = "LOWER(TRIM(CASE WHEN TRIM(source.series_group) <> '' THEN source.series_group ELSE source.name END))"
        val cardOrder = if (sortAlphabetically) "card.is_adult ASC, card.name COLLATE NOCASE ASC" else "card.is_adult ASC, card.rowid ASC"
        val sql = "SELECT card.* FROM $TABLE card INNER JOIN (" +
            "SELECT MIN(source.rowid) AS first_rowid FROM $TABLE source WHERE $sourceFilter GROUP BY $sourceIdentity" +
            ") roots ON card.rowid = roots.first_rowid ORDER BY $cardOrder LIMIT $limit OFFSET $offset"
        val grouped = runCatching {
            db.rawQuery(sql, sourceArgs.toTypedArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(readEntry(cursor))
                }
            }
        }.getOrDefault(emptyList())
        if (grouped.isNotEmpty()) return grouped
        return queryPage(MediaKind.SERIES, group, search, hidden, emptySet(), sortAlphabetically, limit, offset, false, includeAdult)
    }

    private fun seriesFilter(alias: String, group: String, search: String, hidden: Set<String>, includeAdult: Boolean): Pair<String, List<String>> {
        val where = mutableListOf("$alias.kind=?")
        val args = mutableListOf(MediaKind.SERIES.name)
        when {
            group == ContentSafety.LOCKED_CATEGORY -> where += if (includeAdult) "$alias.is_adult=1" else "0"
            group != "Todos" -> { where += "$alias.group_title=? AND $alias.is_adult=0"; args += group }
            !includeAdult -> where += "$alias.is_adult=0"
        }
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

    fun querySeriesSeasons(seriesGroup: String, group: String, hidden: Set<String>, includeAdult: Boolean = false): List<String> {
        val db = helper.readableDatabase
        val where = mutableListOf("kind=?", "series_group=?")
        val args = mutableListOf(MediaKind.SERIES.name, seriesGroup)
        when {
            group == ContentSafety.LOCKED_CATEGORY -> where += if (includeAdult) "is_adult=1" else "0"
            group != "Todos" -> { where += "group_title=? AND is_adult=0"; args += group }
            !includeAdult -> where += "is_adult=0"
        }
        if (hidden.isNotEmpty()) where += "UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})".also { args.addAll(hidden.map(String::uppercase)) }
        val seasonExpr = "CASE WHEN TRIM(season) = '' THEN '1' ELSE season END"
        val sql = "SELECT DISTINCT $seasonExpr AS season_value FROM $TABLE WHERE ${where.joinToString(" AND ")} ORDER BY CAST($seasonExpr AS INTEGER), season_value"
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.getString(0).orEmpty().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    fun querySeriesEpisodes(seriesGroup: String, season: String, group: String, hidden: Set<String>, includeAdult: Boolean = false): List<CatalogEntry> {
        val db = helper.readableDatabase
        val where = mutableListOf("kind=?", "series_group=?")
        val args = mutableListOf(MediaKind.SERIES.name, seriesGroup)
        val seasonExpr = "CASE WHEN TRIM(season) = '' THEN '1' ELSE season END"
        where += "$seasonExpr=?"
        args += season
        when {
            group == ContentSafety.LOCKED_CATEGORY -> where += if (includeAdult) "is_adult=1" else "0"
            group != "Todos" -> { where += "group_title=? AND is_adult=0"; args += group }
            !includeAdult -> where += "is_adult=0"
        }
        if (hidden.isNotEmpty()) where += "UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})".also { args.addAll(hidden.map(String::uppercase)) }
        val sql = "SELECT * FROM $TABLE WHERE ${where.joinToString(" AND ")} ORDER BY CAST(NULLIF(episode, '') AS INTEGER), name COLLATE NOCASE"
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(readEntry(cursor))
            }
        }
    }

    fun updateMetadata(key: String, metadata: CatalogMetadata) {
        val values = ContentValues()
        if (metadata.synopsis.isNotBlank()) values.put("synopsis", metadata.synopsis)
        if (metadata.year.isNotBlank()) values.put("year", metadata.year)
        if (metadata.backdrop.isNotBlank()) values.put("backdrop_url", metadata.backdrop)
        if (metadata.trailer.isNotBlank()) values.put("trailer_url", metadata.trailer)
        if (values.size() > 0) helper.writableDatabase.update(TABLE, values, "item_key=?", arrayOf(key))
    }

    fun close() = helper.close()

    private fun hiddenClause(hidden: Set<String>, args: MutableList<String>): String {
        if (hidden.isEmpty()) return ""
        args.addAll(hidden.map(String::uppercase))
        return "AND UPPER(group_title) NOT IN (${hidden.joinToString(",") { "?" }})"
    }

    private fun aggregateStats(db: SQLiteDatabase, knownTotal: Int? = null): Stats {
        val sql = "SELECT COUNT(*), SUM(CASE WHEN kind='LIVE' THEN 1 ELSE 0 END), SUM(CASE WHEN kind='MOVIE' THEN 1 ELSE 0 END), SUM(CASE WHEN kind='SERIES' THEN 1 ELSE 0 END), COUNT(DISTINCT group_title) FROM $TABLE"
        return db.rawQuery(sql, null).use { cursor ->
            if (!cursor.moveToFirst()) return@use Stats(knownTotal ?: 0, 0, 0, 0, 0)
            Stats(
                knownTotal ?: cursor.getInt(0),
                cursor.getInt(1),
                cursor.getInt(2),
                cursor.getInt(3),
                cursor.getInt(4),
            )
        }
    }

    private fun bind(statement: android.database.sqlite.SQLiteStatement, e: CatalogEntry) {
        statement.bindString(1, e.key)
        statement.bindString(2, e.name)
        statement.bindString(3, e.groupTitle)
        statement.bindString(4, e.tvgId)
        statement.bindString(5, e.logoUrl)
        statement.bindString(6, e.streamUrl)
        statement.bindString(7, e.kind.name)
        statement.bindString(8, e.quality)
        statement.bindString(9, e.seriesGroup)
        statement.bindString(10, e.season)
        statement.bindString(11, e.episode)
        statement.bindString(12, e.year)
        statement.bindString(13, e.synopsis)
        statement.bindString(14, e.cast)
        statement.bindString(15, e.backdropUrl)
        statement.bindString(16, e.trailerUrl)
        statement.bindString(17, e.runtime)
        statement.bindLong(18, if (ContentSafety.isAdult(e)) 1L else 0L)
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

    private class Helper(context: Context) : SQLiteOpenHelper(context, "excellence_catalog.db", null, 3) {
        init {
            // WAL permite leitores (MainActivity, com sua própria conexão) lerem
            // o banco enquanto outra conexão (o importador, em ActivationActivity)
            // ainda está escrevendo em segundo plano. Sem isso, o leitor recebe
            // "database is locked" durante a importação de listas grandes, e o
            // código de consulta engolia esse erro devolvendo uma lista vazia --
            // dando a falsa impressão de "catálogo pronto" com a tela em branco.
            setWriteAheadLoggingEnabled(true)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            // Rede de segurança: se algum ponto ainda esbarrar em um lock breve
            // (ex.: durante o rebuild dos índices ao final da importação, que
            // precisa de acesso exclusivo), espera até 5s em vez de falhar na hora.
            // PRAGMA busy_timeout devolve uma linha de resultado, então precisa
            // ser executado via rawQuery -- execSQL lança SQLiteException para
            // qualquer comando que retorne resultado.
            db.rawQuery("PRAGMA busy_timeout=5000", null)?.use { it.moveToFirst() }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $TABLE (item_key TEXT PRIMARY KEY, name TEXT NOT NULL, group_title TEXT NOT NULL, tvg_id TEXT, logo_url TEXT, stream_url TEXT NOT NULL, kind TEXT NOT NULL, quality TEXT, series_group TEXT, season TEXT, episode TEXT, year TEXT, synopsis TEXT, cast TEXT, backdrop_url TEXT, trailer_url TEXT, runtime TEXT, is_adult INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX idx_catalog_kind_group ON $TABLE(kind, group_title)")
            db.execSQL("CREATE INDEX idx_catalog_name ON $TABLE(name COLLATE NOCASE)")
            db.execSQL("CREATE INDEX idx_catalog_series_season ON $TABLE(kind, series_group, season)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_series_season ON $TABLE(kind, series_group, season)")
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN is_adult INTEGER NOT NULL DEFAULT 0")
                ContentSafety.migrationTerms().forEach { term ->
                    db.execSQL(
                        "UPDATE $TABLE SET is_adult=1 WHERE LOWER(group_title || ' ' || name) LIKE ?",
                        arrayOf("%$term%"),
                    )
                }
            }
        }
    }

    private companion object { const val TABLE = "catalog_items" }
}
