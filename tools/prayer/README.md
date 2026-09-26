# tools/prayer — DS-6B Task 5 offline city index pipeline

> **NOT app code.** Lives outside the Gradle source sets (mirrors `tools/nlu/`'s precedent).
> Produces the bundled asset `data/prayer/src/main/assets/prayer/cities.gz`, consumed offline (zero
> network, zero permissions) by `:data:prayer`'s `BundledCityIndex` — the primary manual
> prayer-location path.

## What this is

`build_city_index.py` downloads GeoNames' `cities15000` dump (all cities with population > 15000,
or national capitals — GeoNames' own README says "ca 25.000" for capitals below that population
floor), filters it down by population, and writes a gzipped, pipe-separated asset. Standard library
only (`urllib`/`zipfile`/`csv`/`gzip`) — no `pip install` needed.

## Source data & license (CC-BY 4.0 — attribution required)

- **Source:** GeoNames, https://www.geonames.org — `cities15000.zip`,
  https://download.geonames.org/export/dump/cities15000.zip
- **Dump date (this generation run):** `Last-Modified: Fri, 07 Aug 2026 01:54:16 GMT` (HTTP header
  from `download.geonames.org` at generation time — GeoNames does not stamp an in-file dump date).
- **License:** Creative Commons Attribution 4.0 License —
  https://creativecommons.org/licenses/by/4.0/
- **Attribution line (required by the license; also embedded verbatim as line 1 of the generated
  asset, a `#`-prefixed comment the Kotlin parser skips):**

  > This file is derived from GeoNames (https://www.geonames.org), used under the Creative Commons
  > Attribution 4.0 License (https://creativecommons.org/licenses/by/4.0/). Data source:
  > cities15000.zip (cities with population > 15000 or national capitals).

## Filter

- **Population threshold:** keep rows with `population >= 30000`, regardless of feature class.
- **Golden-city safety net:** the four DS-6B Task-4 golden cities (Makkah/SA, Istanbul/TR,
  London/GB, Kazan/RU — by exact GeoNames `geonameid`, see `GOLDEN_GEONAME_IDS` in the script) are
  always retained even if a future threshold change would otherwise drop one; the build **fails
  loudly** (raises, no partial asset written) if any of the four is missing from the dump. In this
  run all four already cleared the population threshold on their own — the safety net is currently
  inert, not load-bearing.
- **Threshold choice:** measured against the live 2026-08-07 dump across several candidate
  thresholds (population, row count, gzipped size):

  | threshold | rows | gzipped |
  |---|---|---|
  | ≥ 15,000 (full dump) | 34,033 | — |
  | ≥ 20,000 | 27,462 | — |
  | ≥ 25,000 | 22,804 | — |
  | **≥ 30,000 (chosen)** | **19,481** | **286.8 KB** |
  | ≥ 35,000 | 16,952 | 250.7 KB |
  | ≥ 40,000 | 15,096 | 224.3 KB |
  | ≥ 45,000 | 13,528 | 201.8 KB |

  `≥ 30,000` was chosen because it lands at the top of the plan's 10–20k row target band (maximizing
  city coverage / launch relevance) while still leaving comfortable headroom under the ~500 KB
  budget (286.8 KB ≈ 57% of budget).

## Row format

One city per line (`\n`-terminated), UTF-8, pipe-separated, **no header columns** (line 1 is the
CC-BY comment above, not a column header):

```
asciiName|displayName|countryCode|lat2dp|lon2dp|tzId
```

| field | source (GeoNames column) | notes |
|---|---|---|
| `asciiName` | `asciiname` | plain ASCII; used for the diacritic-agnostic secondary match |
| `displayName` | `name` | native spelling/diacritics kept; becomes `PrayerLocation.label` |
| `countryCode` | `country code` | ISO-3166 alpha-2 |
| `lat2dp` | `latitude`, rounded | `%.2f`, matches the `PrayerLocation` domain contract (coordinates must already be pre-rounded to 2dp) |
| `lon2dp` | `longitude`, rounded | `%.2f` |
| `tzId` | `timezone` | IANA zone id, e.g. `Europe/Istanbul` — already GeoNames-native, no remapping |

Exact-duplicate lines are de-duplicated; rows with a blank `asciiname`/`name`/`country code`/
`timezone`, an unparseable population/lat/lon, or out-of-range coordinates are skipped (not fatal to
the build).

## Result of this generation run (2026-08-07)

- **Row count:** 19,481
- **Raw size:** 980,448 bytes (957.5 KB)
- **Gzipped size:** 293,648 bytes (286.8 KB) — well within the ~500 KB target
- **Golden cities confirmed present** (by exact `geonameid`, not just name — GeoNames' own
  canonical name for Mecca *is* "Makkah", not "Mecca"; the Kazan row used is the Tatarstan, RU one,
  `geonameid 551487`, not the same-named `geonameid 743615` town in Ankara Province, TR):
  - `104515` → `Makkah|Makkah|SA|21.43|39.83|Asia/Riyadh`
  - `745044` → `Istanbul|Istanbul|TR|41.01|28.95|Europe/Istanbul`
  - `2643743` → `London|London|GB|51.51|-0.13|Europe/London`
  - `551487` → `Kazan|Kazan|RU|55.79|49.12|Europe/Moscow`

  (Note: the DS-6B Task-4 golden test uses a slightly different Makkah coordinate,
  `lat2dp=21.42`, sourced from the Masjid al-Haram itself rather than GeoNames' city centroid,
  `21.43`. This is an independent, deliberate difference — the city index picks a location for the
  user to *select*, the Task-4 test fixture is a hand-picked golden coordinate for calculation
  correctness — not a data inconsistency.)

## Regenerating the asset

```bash
python3 tools/prayer/build_city_index.py
```

Requires network access (~3.3 MB download from `download.geonames.org`). Overwrites
`data/prayer/src/main/assets/prayer/cities.gz` in place. Re-running against an unchanged GeoNames
dump reproduces a byte-identical gzip (the script pins `mtime=0` and an empty embedded filename in
the gzip header) — but GeoNames' live data changes over time, so a re-run at a later date will
naturally pick up upstream corrections/new cities and its exact byte output will differ; the row
count/size table above is a point-in-time record of this specific 2026-08-07 run, not a promise of
future stability.

## Consumed by

`data/prayer/src/main/java/com/sidr/launcher/data/prayer/BundledCityIndex.kt` — see its KDoc for the
parsing/search contract (lazy off-main parse, case- and diacritic-insensitive prefix-then-contains
search, `PrayerLocation(source = CITY)`). Verified by
`data/prayer/src/test/java/com/sidr/launcher/data/prayer/BundledCityIndexTest.kt` against this exact
generated file via a `File`/`FileInputStream`-backed `assetOpener` — no Android, no network, no
`Context`.
