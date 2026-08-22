import sqlite3

conn = sqlite3.connect(':memory:')
conn.execute('CREATE TABLE catalog_items (item_key TEXT PRIMARY KEY, name TEXT, group_title TEXT, tvg_id TEXT, logo_url TEXT, stream_url TEXT, kind TEXT, quality TEXT, series_group TEXT, season TEXT, episode TEXT, year TEXT, synopsis TEXT, cast TEXT, backdrop_url TEXT, trailer_url TEXT, runtime TEXT)')
rows = [
    ('last', 'Pica-Pau S02E13', 'Series | Netflix', '', '', 'url-last', 'SERIES', '', 'Pica-Pau', '2', '13', '', '', '', '', '', ''),
    ('first', 'Pica-Pau S01E01', 'Series | Netflix', '', '', 'url-first', 'SERIES', '', 'Pica-Pau', '1', '1', '', '', '', '', '', ''),
    ('middle', 'Pica-Pau S01E02', 'Series | Netflix', '', '', 'url-middle', 'SERIES', '', 'Pica-Pau', '1', '2', '', '', '', '', '', ''),
]
conn.executemany('INSERT INTO catalog_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)', rows)

filter_sql = 'source.kind=? AND source.group_title=?'
args = ('SERIES', 'Series | Netflix')
identity = "CASE WHEN TRIM(source.series_group) <> '' THEN source.series_group ELSE source.name END"
identity_sql = 'SELECT ' + identity + ' AS series_identity FROM catalog_items source WHERE ' + filter_sql + ' GROUP BY series_identity ORDER BY MIN(source.rowid) ASC LIMIT 20 OFFSET 0'
identities = [row[0] for row in conn.execute(identity_sql, args)]
assert identities == ['Pica-Pau'], identities

item_filter = 'item.kind=? AND item.group_title=?'
item_identity = "CASE WHEN TRIM(item.series_group) <> '' THEN item.series_group ELSE item.name END"
representative_sql = 'SELECT * FROM catalog_items item WHERE ' + item_filter + ' AND ' + item_identity + '=? ORDER BY COALESCE(CAST(NULLIF(item.season, \'\') AS INTEGER), 1), COALESCE(CAST(NULLIF(item.episode, \'\') AS INTEGER), 0), item.rowid LIMIT 1'
representative = conn.execute(representative_sql, args + (identities[0],)).fetchone()
assert representative[0] == 'first', representative

seasons = [row[0] for row in conn.execute("SELECT DISTINCT season FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' ORDER BY CAST(season AS INTEGER)")]
assert seasons == ['1', '2'], seasons
episodes = [row[0] for row in conn.execute("SELECT item_key FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' AND season='1' ORDER BY CAST(NULLIF(episode, '') AS INTEGER), name COLLATE NOCASE")]
assert episodes == ['first', 'middle'], episodes
print('OK: series card appears, representative=S01E01, seasons=1/2, episodes=E01/E02')
