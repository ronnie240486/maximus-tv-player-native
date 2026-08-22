import sqlite3

conn = sqlite3.connect(':memory:')
conn.execute('CREATE TABLE catalog_items (item_key TEXT PRIMARY KEY, name TEXT, group_title TEXT, tvg_id TEXT, logo_url TEXT, stream_url TEXT, kind TEXT, quality TEXT, series_group TEXT, season TEXT, episode TEXT, year TEXT, synopsis TEXT, cast TEXT, backdrop_url TEXT, trailer_url TEXT, runtime TEXT)')
rows = [
    ('last', 'Pica-Pau S02E13', 'Series | Netflix', '', '', 'url-last', 'SERIES', '', 'Pica-Pau', '2', '13', '', '', '', '', '', ''),
    ('first', 'Pica-Pau S01E01', 'Series | Netflix', '', '', 'url-first', 'SERIES', '', 'Pica-Pau', '1', '1', '', '', '', '', '', ''),
    ('middle', 'Pica-Pau S01E02', 'Series | Netflix', '', '', 'url-middle', 'SERIES', '', 'Pica-Pau', '1', '2', '', '', '', '', '', ''),
]
conn.executemany('INSERT INTO catalog_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)', rows)

filter_sql = 'item.kind=? AND item.group_title=?'
args = ('SERIES', 'Series | Netflix')
identity = "CASE WHEN TRIM(item.series_group) <> '' THEN item.series_group ELSE item.name END"
identity_sql = 'SELECT series_identity FROM (SELECT ' + identity + ' AS series_identity, item.rowid AS source_rowid FROM catalog_items item WHERE ' + filter_sql + ') GROUP BY series_identity ORDER BY MIN(source_rowid) ASC LIMIT 20 OFFSET 0'
identities = [row[0] for row in conn.execute(identity_sql, args)]
assert identities == ['Pica-Pau'], identities

representative_sql = 'SELECT * FROM catalog_items item WHERE ' + filter_sql + ' AND ' + identity + '=? ORDER BY COALESCE(CAST(NULLIF(item.season, \'\') AS INTEGER), 1), COALESCE(CAST(NULLIF(item.episode, \'\') AS INTEGER), 0), item.rowid LIMIT 1'
representative = conn.execute(representative_sql, args + (identities[0],)).fetchone()
assert representative[0] == 'first', representative

seasons = [row[0] for row in conn.execute("SELECT DISTINCT CASE WHEN TRIM(season) = '' THEN '1' ELSE season END FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' ORDER BY CAST(CASE WHEN TRIM(season) = '' THEN '1' ELSE season END AS INTEGER)")]
assert seasons == ['1', '2'], seasons
episodes = [row[0] for row in conn.execute("SELECT item_key FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' AND season='1' ORDER BY CAST(NULLIF(episode, '') AS INTEGER), name COLLATE NOCASE")]
assert episodes == ['first', 'middle'], episodes
print('OK: categories can exist, series list returns Pica-Pau, representative=S01E01, seasons=1/2, episodes=E01/E02')
