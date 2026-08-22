import sqlite3

conn = sqlite3.connect(':memory:')
conn.execute('CREATE TABLE catalog_items (item_key TEXT PRIMARY KEY, name TEXT, group_title TEXT, tvg_id TEXT, logo_url TEXT, stream_url TEXT, kind TEXT, quality TEXT, series_group TEXT, season TEXT, episode TEXT, year TEXT, synopsis TEXT, cast TEXT, backdrop_url TEXT, trailer_url TEXT, runtime TEXT)')
rows = [
    ('last', 'Pica-Pau S02E13', 'Series | Netflix', '', '', 'url-last', 'SERIES', '', 'Pica-Pau', '2', '13', '', '', '', '', '', ''),
    ('first', 'Pica-Pau S01E01', 'Series | Netflix', '', '', 'url-first', 'SERIES', '', 'Pica-Pau', '1', '1', '', '', '', '', '', ''),
    ('middle', 'Pica-Pau S01E02', 'Series | Netflix', '', '', 'url-middle', 'SERIES', '', 'Pica-Pau', '1', '2', '', '', '', '', '', ''),
]
conn.executemany('INSERT INTO catalog_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)', rows)
outer_filter = 'candidate.kind=? AND candidate.group_title=?'
inner_filter = 'episode.kind=? AND episode.group_title=?'
outer_identity = "CASE WHEN TRIM(candidate.series_group) <> '' THEN candidate.series_group ELSE candidate.name END"
inner_identity = "CASE WHEN TRIM(episode.series_group) <> '' THEN episode.series_group ELSE episode.name END"
sql = 'SELECT candidate.* FROM catalog_items candidate WHERE ' + outer_filter + ' AND candidate.rowid = (SELECT episode.rowid FROM catalog_items episode WHERE ' + inner_filter + ' AND ' + inner_identity + ' = ' + outer_identity + ' ORDER BY COALESCE(CAST(NULLIF(episode.season, \'\') AS INTEGER), 1), COALESCE(CAST(NULLIF(episode.episode, \'\') AS INTEGER), 0), episode.rowid LIMIT 1) ORDER BY candidate.rowid LIMIT 20 OFFSET 0'
representative = conn.execute(sql, ('SERIES', 'Series | Netflix', 'SERIES', 'Series | Netflix')).fetchone()
assert representative[0] == 'first', representative
seasons = [row[0] for row in conn.execute("SELECT DISTINCT CASE WHEN TRIM(season) = '' THEN '1' ELSE season END FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' ORDER BY CAST(CASE WHEN TRIM(season) = '' THEN '1' ELSE season END AS INTEGER)")]
assert seasons == ['1', '2'], seasons
episodes = [row[0] for row in conn.execute("SELECT item_key FROM catalog_items WHERE kind='SERIES' AND series_group='Pica-Pau' AND season='1' ORDER BY CAST(NULLIF(episode, '') AS INTEGER), name COLLATE NOCASE")]
assert episodes == ['first', 'middle'], episodes
print('OK: representative=Pica-Pau S01E01, seasons=1/2, episodes=E01/E02')
