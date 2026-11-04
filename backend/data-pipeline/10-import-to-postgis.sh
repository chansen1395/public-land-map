#!/usr/bin/env bash
# =============================================================================
# 10-import-to-postgis.sh
#
# Imports the datasets downloaded by steps 01-05 into PostGIS with ogr2ogr, then maps
# each source's real field names onto the schema in sql/init.sql.
#
# Tables are cleared before import, so the script is safe to re-run.
#
# Field names below come from each service's public layer metadata, read on 2026-11-04.
# ogr2ogr lowercases column names when it loads a table, so FIPS_NR becomes fips_nr.
# THIS SCRIPT HAS NOT BEEN RUN AGAINST A LIVE DATABASE: the field mappings match the
# metadata, but the SQL itself is unverified. See backend/README.md.
#
# Prerequisites:
#   - PostGIS running (docker compose up -d postgis)
#   - Download scripts 01-05 have completed
#   - GDAL/ogr2ogr and psql installed
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

# ---------------------------------------------------------------------------
# Helper: run SQL against PostGIS via psql
# ---------------------------------------------------------------------------
run_sql() {
    psql "${PSQL_CONN}" -c "$1"
}

# ---------------------------------------------------------------------------
# Helper: check that a source file exists
# ---------------------------------------------------------------------------
require_file() {
    local file="$1"
    local label="$2"
    if [[ ! -f "${file}" ]]; then
        echo "[import] WARNING: ${label} not found at ${file}, skipping."
        return 1
    fi
    return 0
}

# ---------------------------------------------------------------------------
# 1. Import WA Parcels
#    Source fields (Current_Parcels layer 0): OBJECTID, FIPS_NR, COUNTY_NM,
#    PARCEL_ID_NR, LANDUSE_CD. The layer has no owner field. COUNTY_NM is mostly a
#    county number, not a name, so the county name comes from the FIPS code.
#    Acreage is computed from the geometry.
# ---------------------------------------------------------------------------
echo "================================================================="
echo "[import] 1/6 - WA Parcels"
echo "================================================================="

WA_PARCELS="${DATA_DIR}/wa-parcels/wa-parcels-merged.geojson"
if require_file "${WA_PARCELS}" "WA parcels"; then
    echo "[import] Clearing existing WA parcels..."
    run_sql "DELETE FROM parcels WHERE state = 'WA';"
    run_sql "DROP TABLE IF EXISTS _tmp_wa_parcels;"

    echo "[import] Loading WA parcels into a temporary table..."
    ogr2ogr \
        -f "PostgreSQL" "${PG_CONN}" \
        "${WA_PARCELS}" \
        -nln "_tmp_wa_parcels" \
        -overwrite \
        -progress \
        -lco GEOMETRY_NAME=geom \
        -nlt PROMOTE_TO_MULTI

    echo "[import] Mapping WA parcel fields to schema..."
    run_sql "
        WITH wa_counties (fips, name) AS (VALUES
            ('001','Adams'), ('003','Asotin'), ('005','Benton'), ('007','Chelan'), ('009','Clallam'),
            ('011','Clark'), ('013','Columbia'), ('015','Cowlitz'), ('017','Douglas'), ('019','Ferry'),
            ('021','Franklin'), ('023','Garfield'), ('025','Grant'), ('027','Grays Harbor'), ('029','Island'),
            ('031','Jefferson'), ('033','King'), ('035','Kitsap'), ('037','Kittitas'), ('039','Klickitat'),
            ('041','Lewis'), ('043','Lincoln'), ('045','Mason'), ('047','Okanogan'), ('049','Pacific'),
            ('051','Pend Oreille'), ('053','Pierce'), ('055','San Juan'), ('057','Skagit'), ('059','Skamania'),
            ('061','Snohomish'), ('063','Spokane'), ('065','Stevens'), ('067','Thurston'), ('069','Wahkiakum'),
            ('071','Walla Walla'), ('073','Whatcom'), ('075','Whitman'), ('077','Yakima')
        ),
        src AS (
            SELECT t.*,
                   LPAD(COALESCE(NULLIF(t.fips_nr, ''),
                                 NULLIF(REGEXP_REPLACE(COALESCE(t.county_nm, ''), '[^0-9]', '', 'g'), ''),
                                 ''), 3, '0') AS fips3
            FROM _tmp_wa_parcels t
            WHERE t.geom IS NOT NULL
        )
        INSERT INTO parcels (id, parcel_number, county, state, acreage, land_use_code,
                             centroid_lat, centroid_lng, geom, last_updated)
        SELECT
            'WA-' || s.ogc_fid::TEXT,
            COALESCE(s.parcel_id_nr, ''),
            COALESCE(c.name, NULLIF(s.county_nm, ''), ''),
            'WA',
            (ST_Area(s.geom::geography) / 4046.8564224)::REAL,
            s.landuse_cd::TEXT,
            ST_Y(ST_Centroid(s.geom)),
            ST_X(ST_Centroid(s.geom)),
            ST_Multi(ST_SetSRID(s.geom, 4326)),
            EXTRACT(EPOCH FROM NOW())::BIGINT
        FROM src s
        LEFT JOIN wa_counties c ON c.fips = s.fips3
        ON CONFLICT (id) DO UPDATE SET
            parcel_number = EXCLUDED.parcel_number,
            county = EXCLUDED.county,
            acreage = EXCLUDED.acreage,
            land_use_code = EXCLUDED.land_use_code,
            geom = EXCLUDED.geom,
            last_updated = EXCLUDED.last_updated;
    "

    WA_COUNT=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM parcels WHERE state='WA';")
    echo "[import] WA parcels imported: ${WA_COUNT} records"
    run_sql "DROP TABLE IF EXISTS _tmp_wa_parcels;"
fi

# ---------------------------------------------------------------------------
# 2. Import OR Parcels
#    No confirmed statewide source exists (see 02-download-or-parcels.sh). The field
#    names come from OR_PARCEL_FIELD and OR_COUNTY_FIELD, the same variables the
#    download used. Skipped when the file or the variables are missing.
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[import] 2/6 - OR Parcels"
echo "================================================================="

OR_PARCELS="${DATA_DIR}/or-parcels/or-parcels-merged.geojson"
if require_file "${OR_PARCELS}" "OR parcels"; then
    if [[ -z "${OR_PARCEL_FIELD:-}" || -z "${OR_COUNTY_FIELD:-}" ]]; then
        echo "[import] WARNING: set OR_PARCEL_FIELD and OR_COUNTY_FIELD to the names used in step 02, skipping OR parcels."
    else
        for name in "${OR_PARCEL_FIELD}" "${OR_COUNTY_FIELD}"; do
            if [[ ! "${name}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
                echo "[import] ERROR: '${name}' is not a plain field name." >&2
                exit 1
            fi
        done
        OR_PARCEL_COL="${OR_PARCEL_FIELD,,}"
        OR_COUNTY_COL="${OR_COUNTY_FIELD,,}"

        echo "[import] Clearing existing OR parcels..."
        run_sql "DELETE FROM parcels WHERE state = 'OR';"
        run_sql "DROP TABLE IF EXISTS _tmp_or_parcels;"

        ogr2ogr \
            -f "PostgreSQL" "${PG_CONN}" \
            "${OR_PARCELS}" \
            -nln "_tmp_or_parcels" \
            -overwrite \
            -progress \
            -lco GEOMETRY_NAME=geom \
            -nlt PROMOTE_TO_MULTI

        echo "[import] Mapping OR tax lot fields to schema..."
        run_sql "
            INSERT INTO parcels (id, parcel_number, county, state, acreage,
                                 centroid_lat, centroid_lng, geom, last_updated)
            SELECT
                'OR-' || ogc_fid::TEXT,
                COALESCE(${OR_PARCEL_COL}::TEXT, ''),
                COALESCE(${OR_COUNTY_COL}::TEXT, ''),
                'OR',
                (ST_Area(geom::geography) / 4046.8564224)::REAL,
                ST_Y(ST_Centroid(geom)),
                ST_X(ST_Centroid(geom)),
                ST_Multi(ST_SetSRID(geom, 4326)),
                EXTRACT(EPOCH FROM NOW())::BIGINT
            FROM _tmp_or_parcels
            WHERE geom IS NOT NULL
            ON CONFLICT (id) DO UPDATE SET
                parcel_number = EXCLUDED.parcel_number,
                county = EXCLUDED.county,
                acreage = EXCLUDED.acreage,
                geom = EXCLUDED.geom,
                last_updated = EXCLUDED.last_updated;
        "

        OR_COUNT=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM parcels WHERE state='OR';")
        echo "[import] OR parcels imported: ${OR_COUNT} records"
        run_sql "DROP TABLE IF EXISTS _tmp_or_parcels;"
    fi
fi

# ---------------------------------------------------------------------------
# 3. Import PAD-US (Public Lands)
#    Source fields (PAD_US_4_1 layer 0): OBJECTID, Unit_Nm, Loc_Nm, Mang_Type, Mang_Name,
#    Des_Tp, Loc_Ds, Pub_Access, State_Nm, GIS_Acres. Mang_Name, Mang_Type, Des_Tp and
#    Pub_Access are codes; padus-domains.csv (from step 03) holds their descriptions.
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[import] 3/6 - PAD-US Public Lands"
echo "================================================================="

PADUS_FILE="${DATA_DIR}/pad-us/pad-us-wa-or.gpkg"
PADUS_DOMAINS="${DATA_DIR}/pad-us/padus-domains.csv"
if require_file "${PADUS_FILE}" "PAD-US WA/OR" && require_file "${PADUS_DOMAINS}" "PAD-US code tables"; then
    echo "[import] Clearing existing public lands..."
    run_sql "TRUNCATE TABLE public_lands;"
    run_sql "DROP TABLE IF EXISTS _tmp_padus;"
    run_sql "DROP TABLE IF EXISTS _padus_domains;"

    ogr2ogr \
        -f "PostgreSQL" "${PG_CONN}" \
        "${PADUS_FILE}" \
        "padus" \
        -nln "_tmp_padus" \
        -overwrite \
        -progress \
        -lco GEOMETRY_NAME=geom \
        -nlt PROMOTE_TO_MULTI

    echo "[import] Loading PAD-US code tables..."
    run_sql "CREATE TABLE _padus_domains (field TEXT, code TEXT, description TEXT);"
    psql "${PSQL_CONN}" -c "COPY _padus_domains FROM STDIN WITH (FORMAT csv, HEADER true)" < "${PADUS_DOMAINS}"

    echo "[import] Mapping PAD-US fields to public_lands schema..."
    run_sql "
        WITH mapped_padus AS (
            SELECT
                'PADUS-' || p.ogc_fid::TEXT AS id,
                COALESCE(NULLIF(p.unit_nm, ''), NULLIF(p.loc_nm, ''), '') AS unit_name,
                COALESCE(mn.description, p.mang_name, '') AS managing_agency,
                COALESCE(dt.description, p.des_tp, '') AS designation_type,
                CASE COALESCE(pa.description, '')
                    WHEN 'Open Access' THEN 'Open'
                    WHEN 'Restricted Access' THEN 'Restricted'
                    WHEN 'Closed' THEN 'Closed'
                    ELSE 'Unknown'
                END AS public_access,
                COALESCE(p.state_nm, '') AS state,
                COALESCE(p.gis_acres, 0)::REAL AS gis_acres,
                ST_Y(ST_Centroid(p.geom)) AS centroid_lat,
                ST_X(ST_Centroid(p.geom)) AS centroid_lng,
                ST_Multi(ST_SetSRID(p.geom, 4326)) AS geom,
                CONCAT_WS(' ', p.unit_nm, p.loc_nm, mn.description, mt.description,
                          dt.description, p.loc_ds, pa.description) AS classification_text
            FROM _tmp_padus p
            LEFT JOIN _padus_domains mn ON mn.field = 'Mang_Name' AND mn.code = p.mang_name
            LEFT JOIN _padus_domains mt ON mt.field = 'Mang_Type' AND mt.code = p.mang_type
            LEFT JOIN _padus_domains dt ON dt.field = 'Des_Tp' AND dt.code = p.des_tp
            LEFT JOIN _padus_domains pa ON pa.field = 'Pub_Access' AND pa.code = p.pub_access
            WHERE p.geom IS NOT NULL
        )
        INSERT INTO public_lands (id, unit_name, managing_agency, agency_category, designation_type,
                                   public_access, state, gis_acres,
                                   centroid_lat, centroid_lng, geom, last_updated)
        SELECT
            id,
            unit_name,
            managing_agency,
            CASE
                WHEN classification_text ILIKE '%Bureau of Land Management%' OR classification_text ILIKE '%BLM%' THEN 'BLM'
                WHEN classification_text ILIKE '%Forest Service%' OR classification_text ILIKE '%USFS%' THEN 'USFS'
                WHEN classification_text ILIKE '%National Park%' OR classification_text ILIKE '%NPS%' THEN 'National Park Service'
                WHEN classification_text ILIKE '%Fish and Wildlife Service%' AND classification_text NOT ILIKE '%State Fish%' THEN 'Fish & Wildlife Service'
                WHEN classification_text ILIKE '%Army Corps%' OR classification_text ILIKE '%Corps of Engineers%' OR classification_text ILIKE '%Bureau of Reclamation%' THEN 'Army Corps / Reclamation'
                WHEN classification_text ILIKE '%Department of Energy%' OR classification_text ILIKE '%Dept of Energy%' OR classification_text ILIKE '%Defense%' OR classification_text ILIKE '%Military%' THEN 'DOE / Defense'
                WHEN classification_text ILIKE '%Natural Resources%' OR classification_text ILIKE '%DNR%' OR classification_text ILIKE '%Department of Land%' OR classification_text ILIKE '%Land Board%' OR classification_text ILIKE '%State Forest%' OR classification_text ILIKE '%Common School%' THEN 'DNR / State Trust'
                WHEN classification_text ILIKE '%State Park%' THEN 'State Parks'
                WHEN classification_text ILIKE '%State Fish%' OR classification_text ILIKE '%State Wildlife%' OR classification_text ILIKE '%WDFW%' OR classification_text ILIKE '%ODFW%' THEN 'State Wildlife'
                WHEN classification_text ILIKE '%County%' OR classification_text ILIKE '%City%' OR classification_text ILIKE '%Municip%' OR classification_text ILIKE '%Local%' OR classification_text ILIKE '%Regional%' THEN 'Local Government'
                WHEN classification_text ILIKE '%Tribal%' OR classification_text ILIKE '%American Indian%' OR classification_text ILIKE '%Reservation%' THEN 'Tribal'
                WHEN classification_text ILIKE '%Wilderness%' OR classification_text ILIKE '%Conservation%' OR classification_text ILIKE '%Natural Area%' OR classification_text ILIKE '%NRCA%' THEN 'Wilderness / Conservation'
                WHEN classification_text ILIKE '%Federal%' THEN 'Other Federal'
                WHEN classification_text ILIKE '%State%' THEN 'Other State'
                WHEN public_access IN ('Open', 'Restricted') THEN 'Open Access'
                ELSE 'Other Public'
            END,
            designation_type,
            public_access,
            state,
            gis_acres,
            centroid_lat,
            centroid_lng,
            geom,
            EXTRACT(EPOCH FROM NOW())::BIGINT
        FROM mapped_padus
        ON CONFLICT (id) DO UPDATE SET
            unit_name = EXCLUDED.unit_name,
            managing_agency = EXCLUDED.managing_agency,
            agency_category = EXCLUDED.agency_category,
            designation_type = EXCLUDED.designation_type,
            public_access = EXCLUDED.public_access,
            gis_acres = EXCLUDED.gis_acres,
            geom = EXCLUDED.geom,
            last_updated = EXCLUDED.last_updated;
    "

    PL_COUNT=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM public_lands;")
    echo "[import] Public lands imported: ${PL_COUNT} records"

    run_sql "DROP TABLE IF EXISTS _tmp_padus;"
    run_sql "DROP TABLE IF EXISTS _padus_domains;"
fi

# ---------------------------------------------------------------------------
# 4. Import PLSS Grid
#    Source fields (CadNSDI layer 2, PLSS Section): OBJECTID, PLSSID, FRSTDIVID,
#    FRSTDIVNO, FRSTDIVLAB. Township, range, meridian and state come from PLSSID, which
#    is laid out as SS MM TTT F D RRR F D 0, for example WA330010N0020E0:
#    state WA, meridian code 33, township 001 N, range 002 E.
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[import] 4/6 - PLSS Grid"
echo "================================================================="

PLSS_FILE="${DATA_DIR}/plss/plss-wa-or.geojson"
if require_file "${PLSS_FILE}" "PLSS WA/OR"; then
    echo "[import] Clearing existing PLSS grid..."
    run_sql "TRUNCATE TABLE plss_grid;"
    run_sql "DROP TABLE IF EXISTS _tmp_plss;"

    ogr2ogr \
        -f "PostgreSQL" "${PG_CONN}" \
        "${PLSS_FILE}" \
        -nln "_tmp_plss" \
        -overwrite \
        -progress \
        -lco GEOMETRY_NAME=geom \
        -nlt PROMOTE_TO_MULTI

    echo "[import] Mapping PLSS fields to schema..."
    run_sql "
        INSERT INTO plss_grid (id, township, range, section, meridian, state, geom)
        SELECT
            COALESCE(frstdivid, plssid, '') || '-' || ogc_fid::TEXT,
            LTRIM(SUBSTR(plssid, 5, 3), '0') || CASE WHEN SUBSTR(plssid, 8, 1) = '0' THEN '' ELSE '.' || SUBSTR(plssid, 8, 1) END || SUBSTR(plssid, 9, 1),
            LTRIM(SUBSTR(plssid, 10, 3), '0') || CASE WHEN SUBSTR(plssid, 13, 1) = '0' THEN '' ELSE '.' || SUBSTR(plssid, 13, 1) END || SUBSTR(plssid, 14, 1),
            COALESCE(frstdivno, ''),
            SUBSTR(plssid, 3, 2),
            SUBSTR(plssid, 1, 2),
            ST_Multi(ST_SetSRID(geom, 4326))
        FROM _tmp_plss
        WHERE geom IS NOT NULL
        ON CONFLICT (id) DO UPDATE SET
            township = EXCLUDED.township,
            range = EXCLUDED.range,
            section = EXCLUDED.section,
            meridian = EXCLUDED.meridian,
            state = EXCLUDED.state,
            geom = EXCLUDED.geom;
    "

    PLSS_COUNT=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM plss_grid;")
    echo "[import] PLSS sections imported: ${PLSS_COUNT} records"
    run_sql "DROP TABLE IF EXISTS _tmp_plss;"
fi

# ---------------------------------------------------------------------------
# 5. Import USFS Roads and Trails
#    State is a rough guess from latitude: the Columbia River border between WA and OR
#    wanders between about 45.5 and 46.3 degrees north, so features near it can be wrong.
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[import] 5/6 - USFS Roads & Trails"
echo "================================================================="

echo "[import] Clearing existing roads_trails..."
run_sql "TRUNCATE TABLE roads_trails;"

# -- Roads (EDW_RoadBasic_01 layer 0): OBJECTID, NAME, ROUTE_STATUS, SURFACE_TYPE, OPER_MAINT_LEVEL.
#    This layer holds roads open to motor vehicles; closed roads are layer 1, which is not loaded.
ROADS_FILE="${DATA_DIR}/usfs/usfs-roads-pnw.geojson"
if require_file "${ROADS_FILE}" "USFS Roads"; then
    run_sql "DROP TABLE IF EXISTS _tmp_roads;"

    ogr2ogr \
        -f "PostgreSQL" "${PG_CONN}" \
        "${ROADS_FILE}" \
        -nln "_tmp_roads" \
        -overwrite \
        -progress \
        -lco GEOMETRY_NAME=geom \
        -nlt PROMOTE_TO_MULTI

    echo "[import] Mapping USFS roads to schema..."
    run_sql "
        INSERT INTO roads_trails (id, name, route_type, status, surface_type, allowed_uses,
                                  managing_agency, state, geom, last_updated)
        SELECT
            'USFS-RD-' || ogc_fid::TEXT,
            COALESCE(name, ''),
            'road',
            'open',
            NULLIF(surface_type, ''),
            'motor_vehicle',
            'USFS',
            CASE WHEN ST_Y(ST_Centroid(geom)) >= 46.0 THEN 'WA' ELSE 'OR' END,
            ST_Multi(ST_SetSRID(geom, 4326)),
            EXTRACT(EPOCH FROM NOW())::BIGINT
        FROM _tmp_roads
        WHERE geom IS NOT NULL;
    "

    ROAD_COUNT=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM roads_trails WHERE route_type='road';")
    echo "[import] USFS roads imported: ${ROAD_COUNT} records"
    run_sql "DROP TABLE IF EXISTS _tmp_roads;"
fi

# -- Trails (EDW_TrailNFSPublish_01 layer 0): OBJECTID, TRAIL_NAME, TRAIL_TYPE, TRAIL_SURFACE and
#    the HIKER_PEDESTRIAN_, PACK_SADDLE_, BICYCLE_, MOTORCYCLE_, ATV_ and FOURWD_MANAGED fields.
#    Each MANAGED field holds the season the use is managed for, so any value means the use is allowed.
#    The service has no open or closed status, so every trail is loaded as open.
TRAILS_FILE="${DATA_DIR}/usfs/usfs-trails-pnw.geojson"
if require_file "${TRAILS_FILE}" "USFS Trails"; then
    run_sql "DROP TABLE IF EXISTS _tmp_trails;"

    ogr2ogr \
        -f "PostgreSQL" "${PG_CONN}" \
        "${TRAILS_FILE}" \
        -nln "_tmp_trails" \
        -overwrite \
        -progress \
        -lco GEOMETRY_NAME=geom \
        -nlt PROMOTE_TO_MULTI

    echo "[import] Mapping USFS trails to schema..."
    run_sql "
        INSERT INTO roads_trails (id, name, route_type, status, surface_type, allowed_uses,
                                  managing_agency, state, geom, last_updated)
        SELECT
            'USFS-TR-' || ogc_fid::TEXT,
            COALESCE(trail_name, ''),
            'trail',
            'open',
            NULLIF(trail_surface, ''),
            CONCAT_WS(',',
                CASE WHEN COALESCE(hiker_pedestrian_managed, '') <> '' THEN 'hiking' END,
                CASE WHEN COALESCE(bicycle_managed, '') <> '' THEN 'biking' END,
                CASE WHEN COALESCE(pack_saddle_managed, '') <> '' THEN 'equestrian' END,
                CASE WHEN COALESCE(motorcycle_managed, '') <> ''
                       OR COALESCE(atv_managed, '') <> ''
                       OR COALESCE(fourwd_managed, '') <> '' THEN 'motor_vehicle' END
            ),
            'USFS',
            CASE WHEN ST_Y(ST_Centroid(geom)) >= 46.0 THEN 'WA' ELSE 'OR' END,
            ST_Multi(ST_SetSRID(geom, 4326)),
            EXTRACT(EPOCH FROM NOW())::BIGINT
        FROM _tmp_trails
        WHERE geom IS NOT NULL;
    "

    TRAIL_COUNT=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM roads_trails WHERE route_type='trail';")
    echo "[import] USFS trails imported: ${TRAIL_COUNT} records"
    run_sql "DROP TABLE IF EXISTS _tmp_trails;"
fi

# ---------------------------------------------------------------------------
# 6. Import USFS Recreation Sites
#    Source fields (EDW_RecreationOpportunities_01 layer 0): OBJECTID, RECAREAID, RECAREANAME,
#    MARKERACTIVITY, MARKERACTIVITYGROUP, RECAREADESCRIPTION, FEEDESCRIPTION, FORESTNAME,
#    LATITUDE, LONGITUDE. The layer lists one row per activity at a site, so rows are
#    reduced to one per site and type, and only the five types in the schema are kept.
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[import] 6/6 - USFS Recreation Sites"
echo "================================================================="

REC_FILE="${DATA_DIR}/usfs/usfs-recreation-pnw.geojson"
if require_file "${REC_FILE}" "USFS Recreation Sites"; then
    echo "[import] Clearing existing recreation sites..."
    run_sql "TRUNCATE TABLE recreation_sites;"
    run_sql "DROP TABLE IF EXISTS _tmp_recreation;"

    ogr2ogr \
        -f "PostgreSQL" "${PG_CONN}" \
        "${REC_FILE}" \
        -nln "_tmp_recreation" \
        -overwrite \
        -progress \
        -lco GEOMETRY_NAME=geom

    echo "[import] Mapping USFS recreation fields to schema..."
    run_sql "
        INSERT INTO recreation_sites (id, name, site_type, managing_agency, state,
                                      latitude, longitude, geom, description,
                                      amenities, fee, last_updated)
        SELECT DISTINCT ON (id)
            id, name, site_type, 'USFS',
            CASE WHEN latitude >= 46.0 THEN 'WA' ELSE 'OR' END,
            latitude, longitude, ST_SetSRID(geom, 4326), description,
            amenities, fee, EXTRACT(EPOCH FROM NOW())::BIGINT
        FROM (
            SELECT
                'USFS-REC-' || COALESCE(recareaid::TEXT, r.ogc_fid::TEXT) || '-' || typed.site_type AS id,
                COALESCE(recareaname, '') AS name,
                typed.site_type AS site_type,
                ST_Y(geom) AS latitude,
                ST_X(geom) AS longitude,
                geom,
                LEFT(COALESCE(recareadescription, ''), 500) AS description,
                COALESCE(markeractivity, '') AS amenities,
                COALESCE(feedescription, '') AS fee
            FROM _tmp_recreation r
            CROSS JOIN LATERAL (
                SELECT CASE
                    WHEN r.markeractivity ILIKE '%camp%' THEN 'campground'
                    WHEN r.markeractivity ILIKE 'Trailhead%' THEN 'trailhead'
                    WHEN r.markeractivity ILIKE '%picnic%' THEN 'picnic_area'
                    WHEN r.markeractivity ILIKE 'Boating%' THEN 'boat_ramp'
                    WHEN r.markeractivity ILIKE '%viewing%' OR r.markeractivity ILIKE '%scenic%' THEN 'viewpoint'
                END AS site_type
            ) typed
            WHERE r.geom IS NOT NULL AND typed.site_type IS NOT NULL
        ) sites
        ORDER BY id;
    "

    REC_COUNT=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM recreation_sites;")
    echo "[import] Recreation sites imported: ${REC_COUNT} records"
    run_sql "DROP TABLE IF EXISTS _tmp_recreation;"
fi

# ---------------------------------------------------------------------------
# Update metadata
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[import] Updating metadata..."
echo "================================================================="
run_sql "
    UPDATE data_metadata SET value = NOW()::TEXT, updated_at = NOW() WHERE key = 'last_updated';
    UPDATE data_metadata SET value = '1', updated_at = NOW() WHERE key = 'wa_parcels_version';
    UPDATE data_metadata SET value = '1', updated_at = NOW() WHERE key = 'or_parcels_version';
    UPDATE data_metadata SET value = '1', updated_at = NOW() WHERE key = 'pad_us_version';
"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[import] Import Summary"
echo "================================================================="
psql "${PSQL_CONN}" -c "
    SELECT 'parcels (WA)' AS dataset, COUNT(*) AS records FROM parcels WHERE state='WA'
    UNION ALL
    SELECT 'parcels (OR)', COUNT(*) FROM parcels WHERE state='OR'
    UNION ALL
    SELECT 'public_lands', COUNT(*) FROM public_lands
    UNION ALL
    SELECT 'plss_grid', COUNT(*) FROM plss_grid
    UNION ALL
    SELECT 'roads_trails (roads)', COUNT(*) FROM roads_trails WHERE route_type='road'
    UNION ALL
    SELECT 'roads_trails (trails)', COUNT(*) FROM roads_trails WHERE route_type='trail'
    UNION ALL
    SELECT 'recreation_sites', COUNT(*) FROM recreation_sites;
"

echo "[import] All imports complete."
