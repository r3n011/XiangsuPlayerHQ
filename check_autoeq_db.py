import sqlite3

con = sqlite3.connect(r'e:\PixelPlayer-master\app\src\main\assets\autoeq\headphone_presets.db')
cur = con.cursor()

print('=== TABLES ===')
for row in cur.execute("SELECT name FROM sqlite_master WHERE type='table'"):
    print(row)

print()
print('=== headphone_presets schema ===')
for row in cur.execute('PRAGMA table_info(headphone_presets)'):
    print(row)

print()
print('=== headphone_eq_bands schema ===')
for row in cur.execute('PRAGMA table_info(headphone_eq_bands)'):
    print(row)

print()
print('=== preset count ===', cur.execute('SELECT COUNT(*) FROM headphone_presets').fetchone()[0])
print('=== band count ===', cur.execute('SELECT COUNT(*) FROM headphone_eq_bands').fetchone()[0])

print()
print('=== sample presets ===')
for row in cur.execute('SELECT id, name, brand, category FROM headphone_presets LIMIT 10'):
    print(row)

print()
print('=== distinct filter_type ===')
for row in cur.execute('SELECT filter_type, COUNT(*) FROM headphone_eq_bands GROUP BY filter_type'):
    print(row)

print()
print('=== sample bands for preset 1 ===')
for row in cur.execute('SELECT filter_order, filter_type, frequency, q, gain FROM headphone_eq_bands WHERE preset_id=1 ORDER BY filter_order'):
    print(row)

print()
print('=== gain value range ===')
print(cur.execute('SELECT MIN(gain), MAX(gain) FROM headphone_eq_bands').fetchone())
print('=== frequency range ===')
print(cur.execute('SELECT MIN(frequency), MAX(frequency) FROM headphone_eq_bands').fetchone())
print('=== q value range ===')
print(cur.execute('SELECT MIN(q), MAX(q) FROM headphone_eq_bands').fetchone())

print()
print('=== presets with zero bands (up to 20) ===')
print(cur.execute('SELECT p.id, p.name, (SELECT COUNT(*) FROM headphone_eq_bands b WHERE b.preset_id=p.id) FROM headphone_presets p WHERE (SELECT COUNT(*) FROM headphone_eq_bands b WHERE b.preset_id=p.id)=0 LIMIT 20').fetchall())

con.close()
