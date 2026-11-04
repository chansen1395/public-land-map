#!/usr/bin/env bash
# =============================================================================
# 04-download-plss.sh
#
# Downloads Public Land Survey System (PLSS) sections for Washington and Oregon from
# the BLM National Cadastral service via the ArcGIS REST API. The PLSS township, range
# and section grid is how legal land descriptions work across the western United States.
#
# Source:  https://gis.blm.gov/arcgis/rest/services/Cadastral/BLM_Natl_PLSS_CadNSDI/MapServer/2
#          (layer 2, "PLSS Section"; US government work)
# Checked: 2026-11-04 (WA 74774 sections, OR 104015 sections)
#
# The old script queried layer 1, which is the township layer, with STATEABBR. Layer 2
# has no state field, but every PLSSID starts with the two-letter state code
# (for example WA330010N0020E0), so the filter is PLSSID LIKE 'WA%'.
#
# Output: $DATA_DIR/plss/plss-wa-or.geojson  (fields PLSSID, FRSTDIVID, FRSTDIVNO, FRSTDIVLAB)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

PLSS_URL="https://gis.blm.gov/arcgis/rest/services/Cadastral/BLM_Natl_PLSS_CadNSDI/MapServer/2"
PLSS_FIELDS="OBJECTID,PLSSID,FRSTDIVID,FRSTDIVNO,FRSTDIVLAB"
OUT_DIR="${DATA_DIR}/plss"
MERGED_FILE="${OUT_DIR}/plss-wa-or.geojson"
PAGE_SIZE="${ARCGIS_PAGE_SIZE}"

mkdir -p "${OUT_DIR}"

if [[ -f "${MERGED_FILE}" ]]; then
    echo "[plss] Already downloaded: ${MERGED_FILE}"
    echo "[plss] Delete it to download again. Skipping."
    exit 0
fi

download_state() {
    local state="$1"
    local file="${OUT_DIR}/plss-${state,,}.geojson"
    local where="PLSSID%20LIKE%20%27${state}%25%27"

    if [[ -f "${file}" ]]; then
        echo "[plss] ${state} file exists, skipping download."
        return
    fi

    local count
    count=$(curl --fail -sS "${PLSS_URL}/query?where=${where}&returnCountOnly=true&f=json" \
        | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))")
    echo "[plss] ${state}: ${count} sections"
    if [[ "${count}" -eq 0 ]]; then
        echo "[plss] ERROR: the count query returned 0 for ${state}. The service URL may have changed." >&2
        exit 1
    fi

    rm -f "${file}.part"
    ogr2ogr \
        -f "GeoJSON" \
        "${file}.part" \
        "${PLSS_URL}/query?where=${where}&outFields=${PLSS_FIELDS}&f=json" \
        -oo FEATURE_SERVER_PAGING=YES \
        -oo PAGE_SIZE="${PAGE_SIZE}" \
        -t_srs EPSG:4326 \
        -progress \
        -lco RFC7946=YES

    if [[ ! -s "${file}.part" ]]; then
        echo "[plss] ERROR: ${state} download produced an empty file." >&2
        rm -f "${file}.part"
        exit 1
    fi
    mv "${file}.part" "${file}"
    echo "[plss] ${state} sections downloaded: $(du -h "${file}" | cut -f1)"
}

download_state WA
download_state OR

echo "[plss] Merging WA and OR..."
rm -f "${MERGED_FILE}.part"
cp "${OUT_DIR}/plss-wa.geojson" "${MERGED_FILE}.part"
ogr2ogr -f "GeoJSON" -append -update "${MERGED_FILE}.part" "${OUT_DIR}/plss-or.geojson"
mv "${MERGED_FILE}.part" "${MERGED_FILE}"

echo "[plss] Merge complete: ${MERGED_FILE}"
