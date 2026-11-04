#!/usr/bin/env bash
# =============================================================================
# 05-download-usfs-roads-recreation.sh
#
# Downloads US Forest Service data for Region 06 (Pacific Northwest: Washington and
# Oregon) from the Enterprise Data Warehouse (EDW) ArcGIS REST services:
#
#   Roads       EDW_RoadBasic_01 layer 0, National Forest System roads
#   Trails      EDW_TrailNFSPublish_01 layer 0, National Forest System trails (land trails only)
#   Recreation  EDW_RecreationOpportunities_01 layer 0, recreation opportunities
#
# Base URL: https://apps.fs.usda.gov/arcx/rest/services/EDW/   (US government work)
# Checked:  2026-11-04 (region 06: 41070 roads, 10424 trails of all types, 3145 recreation rows)
#
# The old services this script used (EDW_RoadCore_01, EDW_TrailNFS_01 and
# EDW_RecreationSites_01) are gone. The new layers have no REGION field, so the filters
# are: admin_org LIKE '06%' for roads and trails, and forestorgcode LIKE '06%' for recreation.
#
# Output: $DATA_DIR/usfs/usfs-{roads,trails,recreation}-pnw.geojson
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

EDW="https://apps.fs.usda.gov/arcx/rest/services/EDW"
ROADS_URL="${EDW}/EDW_RoadBasic_01/MapServer/0"
TRAILS_URL="${EDW}/EDW_TrailNFSPublish_01/MapServer/0"
RECREATION_URL="${EDW}/EDW_RecreationOpportunities_01/MapServer/0"

# URL-encoded where clauses
ROADS_WHERE="admin_org%20LIKE%20%2706%25%27"
TRAILS_WHERE="admin_org%20LIKE%20%2706%25%27%20AND%20trail_type%3D%27TERRA%27"
RECREATION_WHERE="forestorgcode%20LIKE%20%2706%25%27"

ROADS_FIELDS="objectid,name,route_status,surface_type,oper_maint_level"
TRAILS_FIELDS="objectid,trail_name,trail_type,trail_surface,hiker_pedestrian_managed,pack_saddle_managed,bicycle_managed,motorcycle_managed,atv_managed,fourwd_managed"
RECREATION_FIELDS="objectid,recareaid,recareaname,markeractivity,markeractivitygroup,recareadescription,feedescription,forestname,latitude,longitude"

OUT_DIR="${DATA_DIR}/usfs"
ROADS_FILE="${OUT_DIR}/usfs-roads-pnw.geojson"
TRAILS_FILE="${OUT_DIR}/usfs-trails-pnw.geojson"
RECREATION_FILE="${OUT_DIR}/usfs-recreation-pnw.geojson"
PAGE_SIZE="${ARCGIS_PAGE_SIZE}"

mkdir -p "${OUT_DIR}"

# download LABEL LAYER_URL WHERE FIELDS OUTPUT_FILE
download() {
    local label="$1" layer_url="$2" where="$3" fields="$4" out_file="$5"

    if [[ -f "${out_file}" ]]; then
        echo "[usfs-${label}] Already downloaded: ${out_file}"
        return
    fi

    local count
    count=$(curl --fail -sS "${layer_url}/query?where=${where}&returnCountOnly=true&f=json" \
        | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))")
    echo "[usfs-${label}] ${count} features"
    if [[ "${count}" -eq 0 ]]; then
        echo "[usfs-${label}] ERROR: the count query returned 0. The service URL may have changed." >&2
        exit 1
    fi

    rm -f "${out_file}.part"
    ogr2ogr \
        -f "GeoJSON" \
        "${out_file}.part" \
        "${layer_url}/query?where=${where}&outFields=${fields}&f=json" \
        -oo FEATURE_SERVER_PAGING=YES \
        -oo PAGE_SIZE="${PAGE_SIZE}" \
        -t_srs EPSG:4326 \
        -progress \
        -lco RFC7946=YES

    if [[ ! -s "${out_file}.part" ]]; then
        echo "[usfs-${label}] ERROR: download produced an empty file." >&2
        rm -f "${out_file}.part"
        exit 1
    fi
    mv "${out_file}.part" "${out_file}"
    echo "[usfs-${label}] Downloaded: $(du -h "${out_file}" | cut -f1)"
}

download roads "${ROADS_URL}" "${ROADS_WHERE}" "${ROADS_FIELDS}" "${ROADS_FILE}"
download trails "${TRAILS_URL}" "${TRAILS_WHERE}" "${TRAILS_FIELDS}" "${TRAILS_FILE}"
download recreation "${RECREATION_URL}" "${RECREATION_WHERE}" "${RECREATION_FIELDS}" "${RECREATION_FILE}"

echo "[usfs] All downloads complete."
