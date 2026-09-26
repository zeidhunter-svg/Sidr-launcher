#!/usr/bin/env python3
"""DS-6B Task 5 — builds the offline bundled city index asset.

NOT app code. Lives outside the Gradle source sets (mirrors tools/nlu/'s precedent). Downloads the
GeoNames `cities15000` dump (cities with population > 15000 or capitals), filters by population,
and writes a gzipped, pipe-separated asset consumed by `:data:prayer`'s
`BundledCityIndex` (`data/prayer/src/main/java/com/sidr/launcher/data/prayer/BundledCityIndex.kt`).

Output row format (one city per line, `\\n`-terminated), UTF-8:

    asciiName|displayName|countryCode|lat2dp|lon2dp|tzId

- asciiName   : GeoNames `asciiname` column (plain ASCII; used for a diacritic-agnostic secondary match)
- displayName : GeoNames `name` column (native spelling/diacritics kept; what BundledCityIndex uses
                as PrayerLocation.label)
- countryCode : ISO-3166 alpha-2 (GeoNames `country code` column)
- lat2dp/lon2dp: latitude/longitude rounded to exactly 2 decimal places (`%.2f`), matching the
                `PrayerLocation` domain contract (coordinates must already be pre-rounded)
- tzId        : IANA zone id (GeoNames `timezone` column, e.g. "Europe/Istanbul")

The first line of the file is a `#`-prefixed CC-BY 4.0 attribution comment — required by the
GeoNames license — which the Kotlin parser skips.

Usage:
    python3 tools/prayer/build_city_index.py

Requires network access (downloads ~3.3 MB from download.geonames.org) and only the Python
standard library (urllib/zipfile/csv/gzip) — no pip install needed, mirroring tools/nlu/'s
"stdlib only" scripts.
"""

from __future__ import annotations

import csv
import gzip
import io
import sys
import urllib.request
import zipfile
from pathlib import Path

GEONAMES_URL = "https://download.geonames.org/export/dump/cities15000.zip"
GEONAMES_ENTRY = "cities15000.txt"

# Minimum population to keep a city (GeoNames `population` column). Chosen empirically (see
# tools/prayer/README.md "Filter" section) to land the row count in the plan's 10-20k band while
# keeping the gzipped asset well under the ~500 KB budget.
POPULATION_THRESHOLD = 30_000

# geonameid -> label, for the DS-6B Task-4 golden cities that MUST survive the population filter
# regardless of their population (they are launch-relevant even if a threshold bump ever excluded
# them). Verified against the live dump: all four already clear POPULATION_THRESHOLD comfortably,
# so this is a safety net, not the reason they're present.
#   104515 = Makkah, SA (GeoNames' own canonical name/asciiname IS "Makkah", not "Mecca")
#   745044 = Istanbul, TR
#   2643743 = London, GB
#   551487 = Kazan, RU (Tatarstan; NOT geonameid 743615, a same-named town in TR)
GOLDEN_GEONAME_IDS: dict[int, str] = {
    104515: "Makkah",
    745044: "Istanbul",
    2643743: "London",
    551487: "Kazan",
}

CC_BY_ATTRIBUTION = (
    "# This file is derived from GeoNames (https://www.geonames.org), used under the Creative "
    "Commons Attribution 4.0 License (https://creativecommons.org/licenses/by/4.0/). "
    "Data source: cities15000.zip (cities with population > 15000 or national capitals)."
)

REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT_PATH = REPO_ROOT / "data" / "prayer" / "src" / "main" / "assets" / "prayer" / "cities.gz"


def download_dump() -> tuple[bytes, str]:
    """Downloads cities15000.zip and returns (raw cities15000.txt bytes, dump Last-Modified date)."""
    print(f"Downloading {GEONAMES_URL} ...", file=sys.stderr)
    with urllib.request.urlopen(GEONAMES_URL, timeout=60) as response:
        last_modified = response.headers.get("Last-Modified", "unknown")
        zip_bytes = response.read()
    print(f"Downloaded {len(zip_bytes)} bytes (Last-Modified: {last_modified})", file=sys.stderr)
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        txt_bytes = zf.read(GEONAMES_ENTRY)
    return txt_bytes, last_modified


def build_rows(txt_bytes: bytes) -> list[str]:
    """Parses cities15000.txt and returns the filtered pipe-separated output lines."""
    rows: list[str] = []
    seen_lines: set[str] = set()
    text = txt_bytes.decode("utf-8")
    reader = csv.reader(io.StringIO(text), delimiter="\t")
    golden_found: set[int] = set()

    for record in reader:
        if len(record) < 18:
            continue  # malformed row — skip, don't fail the whole build

        geonameid_raw, name, asciiname = record[0], record[1], record[2]
        lat_raw, lon_raw = record[4], record[5]
        country_code = record[8]
        population_raw = record[14]
        tz_id = record[17]

        try:
            geonameid = int(geonameid_raw)
            lat = float(lat_raw)
            lon = float(lon_raw)
            population = int(population_raw) if population_raw else 0
        except ValueError:
            continue  # malformed row — skip, don't fail the whole build

        is_golden = geonameid in GOLDEN_GEONAME_IDS
        if is_golden:
            golden_found.add(geonameid)
        if population < POPULATION_THRESHOLD and not is_golden:
            continue

        if not asciiname or not name or not country_code or not tz_id:
            continue  # PrayerLocation requires non-blank label/tzId — skip incomplete rows

        lat2dp = round(lat, 2)
        lon2dp = round(lon, 2)
        if lat2dp == 0.0:
            lat2dp = 0.0  # normalize -0.0
        if lon2dp == 0.0:
            lon2dp = 0.0  # normalize -0.0
        if not (-90.0 <= lat2dp <= 90.0) or not (-180.0 <= lon2dp <= 180.0):
            continue  # out of range — skip (guards a malformed source row)

        line = f"{asciiname}|{name}|{country_code}|{lat2dp:.2f}|{lon2dp:.2f}|{tz_id}"
        if line in seen_lines:
            continue
        seen_lines.add(line)
        rows.append(line)

    missing = set(GOLDEN_GEONAME_IDS) - golden_found
    if missing:
        missing_labels = [GOLDEN_GEONAME_IDS[g] for g in missing]
        raise RuntimeError(
            f"Golden cities missing from the GeoNames dump (geonameids {missing}, "
            f"labels {missing_labels}) — the dump format may have changed; investigate before "
            "shipping a city index without them."
        )

    return rows


def write_asset(rows: list[str]) -> tuple[int, int]:
    """Writes the gzipped asset; returns (raw byte count, gzipped byte count)."""
    body = CC_BY_ATTRIBUTION + "\n" + "\n".join(rows) + "\n"
    raw_bytes = body.encode("utf-8")
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    # GzipFile.close() does not close a caller-supplied fileobj, so the raw file is opened via its
    # own `with` to guarantee it is actually closed/flushed to disk. mtime=0 + filename="" keep the
    # gzip header deterministic (byte-identical output across re-runs of an identical input).
    with open(OUTPUT_PATH, "wb") as raw_file:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw_file, mtime=0) as gz:
            gz.write(raw_bytes)
    gz_bytes = OUTPUT_PATH.stat().st_size
    return len(raw_bytes), gz_bytes


def main() -> None:
    txt_bytes, last_modified = download_dump()
    rows = build_rows(txt_bytes)
    raw_size, gz_size = write_asset(rows)

    print(f"GeoNames dump Last-Modified: {last_modified}")
    print(f"Population threshold: >= {POPULATION_THRESHOLD}")
    print(f"Row count: {len(rows)}")
    print(f"Raw size: {raw_size} bytes ({raw_size / 1024:.1f} KB)")
    print(f"Gzipped size: {gz_size} bytes ({gz_size / 1024:.1f} KB)")
    print(f"Written to: {OUTPUT_PATH}")

    for geonameid, label in GOLDEN_GEONAME_IDS.items():
        print(f"  golden city present: geonameid={geonameid} label={label}")


if __name__ == "__main__":
    main()
