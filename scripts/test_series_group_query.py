import sqlite3

conn = sqlite3.connect(':memory:')
conn.execute('CREATE TABLE catalog_items (item_key TEXT PRIMARY KEY, name TEXT, group_title TEXT, tvg_id TEXT, logo_url TEXT, stream_url TEXT, kind TEXT, quality TEXT, series_group TEXT, season TEXT, episode TEXT, year TEXT, synopsis TEXT, cast TEXT, backdrop_url TEXT, trailer_url TEXT, runtime TEXT)')
rows = [
    ('first', 'Pica-Pau S01E01', 'Series | Netflix', '', '', 'url-first', 'SERIES', '', 'Pica-Pau', '1', '1', '', '', '', '', '', ''),
    ('middle', 'Pica-Pau S01E02', 'Series | Netflix', '', '', 'url-middle', 'SERIES', '', 'Pica-Pau', '1', '2', '', '', '', '', '', ''),
    ('last', 'Pica-Pau S02E13', 'Series | Netflix', '', '', 'url-last', 'SERIES', '', 'Pica-Pau', '2', '13', '', '', '', '', '', ''),
]
conn.executemany('INSERT INTO catalog_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)', rows)

# This is the same generic paginated query used by the reference APK.
page = list(conn.execute("SELECT * FROM catalog_items WHERE kind=? AND group_title=? ORDER BY rowid LIMIT 120 OFFSET 0", ('SERIES', 'Series | Netflix')))
assert page, 'the series page must not be empty when series rows exist'
assert {row[0] for row in page} == {'first', 'middle', 'last'}, page

seasons = [row[0] for row in conn.execute("SELECT DISTINCT season FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' ORDER BY CAST(season AS INTEGER)")]
assert seasons == ['1', '2'], seasons
episodes = [row[0] for row in conn.execute("SELECT item_key FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' AND season='1' ORDER BY CAST(NULLIF(episode, '') AS INTEGER), name COLLATE NOCASE")]
assert episodes == ['first', 'middle'], episodes
print('OK: generic series page returns items, seasons=1/2, episodes=E01/E02')
