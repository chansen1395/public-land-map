# Public Land Map backend

The backend is a PostGIS database, a Martin vector tile server, a small Ktor API, and the shell scripts that fill the database with public data for Washington and Oregon. Nothing here ships data. You download it yourself with the scripts.

**The loaders are unverified.** During the public-release cleanup I checked each source URL and field name against the live service's public layer metadata (4 November 2026) and fixed the ones that had died, but I did not start Docker, so I never ran the import SQL, the compose file or the Martin config end to end. The Ktor API builds and passes its 18 tests without a database. Expect to debug the first real run. The shell scripts were syntax-checked with `bash -n` only.

## Layout

```
backend/
├── .env.example                # copy to .env and set PG_PASSWORD (blank on purpose)
├── docker-compose.yml          # PostGIS, Martin and the API
├── sql/init.sql                # schema, applied on first start
├── martin/config.yaml          # tile server config
├── api-server/                 # Ktor API (own Gradle build and wrapper)
├── data-pipeline/              # download, import and tiling scripts
├── data/                       # downloads land here (not committed)
└── tiles/                      # generated MBTiles (not committed)
    └── offline/                # per-county offline bundles
```

## Requirements

| Tool | Version | Used for |
|---|---|---|
| Docker with Compose v2 | 20+ | PostGIS, Martin and the API |
| GDAL (`ogr2ogr`) | 3.4+ | Downloading and converting data |
| `psql` | 14+ | Database steps |
| `curl` and Python 3 | any, 3.8+ | Downloads and JSON parsing in the scripts |
| tippecanoe (with `tile-join`) | 2.0+ | Steps 20 and 21 only |

Install on macOS with `brew install gdal postgresql tippecanoe`. On Debian or Ubuntu, `sudo apt install gdal-bin postgresql-client`, and build tippecanoe from source. On Windows, use WSL2 and follow the Ubuntu steps.

Washington parcels, Oregon tax lots and PAD-US are large. Plan for tens of gigabytes of disk for the downloads, the database and the tiles, and several hours for a full run.

## Start the stack

```bash
cd backend
cp .env.example .env       # set PG_PASSWORD; PG_DB and PG_USER are optional
docker compose up -d
```

This starts PostGIS on port 5432 (applying `sql/init.sql` on the first start), Martin on port 3000 and the API on port 8080. `PG_PASSWORD` is blank in `.env.example` on purpose: compose and the scripts stop with an error until you set one. The scripts and compose read the same `.env` file, and `.env` is not committed.

All three ports are published on `127.0.0.1` only, so nothing is reachable from the network. The Android emulator reaches them as `10.0.2.2` (its alias for the host's loopback), and a phone reaches them through `adb reverse tcp:8080 tcp:8080` and `adb reverse tcp:3000 tcp:3000`. The scripts pass the database password to `psql` and `ogr2ogr` in the `PGPASSWORD` environment variable, never on a command line.

Check that it is up (the examples assume the default database and user name, `publiclandmap`):

```bash
docker compose exec postgis psql -U publiclandmap -d publiclandmap -c "SELECT PostGIS_Version();"
curl http://localhost:3000/catalog
curl http://localhost:8080/health
```

## Load the data

```bash
cd data-pipeline
./run-pipeline.sh                    # all steps
./run-pipeline.sh 10                 # start from step 10
./run-pipeline.sh 20 --only          # run only step 20
```

| Step | Script | What it does |
|---|---|---|
| 01 | `01-download-wa-parcels.sh` | Washington parcels: `OBJECTID`, `FIPS_NR`, `COUNTY_NM`, `PARCEL_ID_NR`, `LANDUSE_CD` only |
| 02 | `02-download-or-parcels.sh` | Oregon tax lots. Does nothing until you set `OR_PARCELS_URL` (see below) |
| 03 | `03-download-pad-us.sh` | PAD-US 4.1 Fee polygons for WA and OR, plus the code tables |
| 04 | `04-download-plss.sh` | PLSS sections |
| 05 | `05-download-usfs-roads-recreation.sh` | Forest Service roads, trails and recreation, Region 06 |
| 10 | `10-import-to-postgis.sh` | Loads everything into PostGIS and maps the fields |
| 11 | `11-normalize-parcels.sh` | Cleans parcel fields |
| 12 | `12-clip-to-states.sh` | Clips data to the WA and OR outlines |
| 20 | `20-generate-tiles.sh` | Builds MBTiles with tippecanoe |
| 21 | `21-generate-offline-bundles.sh` | Builds per-county offline bundles |

`import-public-lands.sh` is a separate loader. It downloads Washington DNR and Oregon PAD-US layers from ArcGIS REST services and writes them straight to the `public_lands` table through the running `plm-postgis` container. I used it for the app screenshots. It overlaps with the PAD-US path in steps 03 and 10, and the two are not reconciled.

Download scripts skip work when their output file exists. Delete the file to fetch it again. They write to a `.part` file first, use `curl --fail`, and stop if a count query returns zero, so an error page is never kept as data. Settings you can override in the environment: `DATA_DIR`, `TILES_DIR`, `PG_HOST`, `PG_PORT`, `PG_DB`, `PG_USER`, `PG_PASSWORD`, `ARCGIS_PAGE_SIZE`.

### What the loaders request

Each download asks for named fields only, never `outFields=*`. No script requests an owner, site address or mailing address field, and the database schema has no column for a site or mailing address. The public Washington parcel layer has no owner field at all, so `owner_name` stays empty for Washington.

### Oregon parcels (unconfirmed source)

The old default, an ORMAP service on `gis.oregongeology.com`, returns 404. I could not confirm a current public statewide Oregon tax lot service: the Oregon Parcel Database layer on `services8.arcgis.com` (`or_cadastral_wm`) returns "Invalid URL", and the Oregon Department of Forestry `StatewideTaxLots` map service needs a token. ORMAP data comes from the county assessors and the Oregon Department of Revenue. Step 02 therefore skips itself until you point it at a public county layer:

```bash
OR_PARCELS_URL=https://.../FeatureServer/0 OR_PARCEL_FIELD=TAXLOT OR_COUNTY_FIELD=COUNTY ./02-download-or-parcels.sh
```

Export the same `OR_PARCEL_FIELD` and `OR_COUNTY_FIELD` when you run step 10. Without them the Oregon parcel import is skipped, and everything else still loads.

Nothing loads the `regulations` table. The fire restriction, closure and OHV layers stay empty until someone writes that loader.

## Verify the data

```bash
docker compose exec postgis psql -U publiclandmap -d publiclandmap -c "
    SELECT 'parcels' AS t, COUNT(*) FROM parcels
    UNION ALL SELECT 'public_lands', COUNT(*) FROM public_lands
    UNION ALL SELECT 'plss_grid', COUNT(*) FROM plss_grid
    UNION ALL SELECT 'roads_trails', COUNT(*) FROM roads_trails
    UNION ALL SELECT 'recreation_sites', COUNT(*) FROM recreation_sites;"
```

## API server

The API is a separate Gradle build in `api-server`.

```bash
cd api-server
./gradlew test                       # 18 tests, no database needed
./gradlew buildFatJar                # build/libs/public-land-map-api.jar
DB_PASSWORD=... java -jar build/libs/public-land-map-api.jar   # uses localhost:5432 unless DATABASE_URL is set
```

Settings come from environment variables: `DATABASE_URL` (a JDBC URL), `DB_USER`, `DB_PASSWORD` (required), `DB_MAX_POOL_SIZE`, `PORT` and `OFFLINE_TILES_DIR`. The API starts even if PostGIS is down, and `/health` then returns 503 with `degraded`.

## Data sources

URLs and field names checked on 4 November 2026. "Confirmed" means the layer metadata and a sample query were read; it does not mean a full download was run.

| Dataset | Source and URL | Status | Terms |
|---|---|---|---|
| Washington parcels | Washington Geoportal "Current Parcels", `https://services.arcgis.com/jsIt88o09Q0r1j8h/arcgis/rest/services/Current_Parcels/FeatureServer/0` | Confirmed. The old URL had the wrong organization ID and layer name. | The service says it is not for legal, engineering or surveying use, and that some counties restrict parcel use. |
| Oregon tax lots | none | **Unconfirmed**, see above | Check the county's terms. |
| PAD-US 4.1 (Fee) | USGS National Map, `https://edits.nationalmap.gov/arcgis/rest/services/PAD-US/PAD_US_4_1/MapServer/0` | Confirmed (WA 6552, OR 6100 features). The old ScienceBase 4.0 file URL returns 404, and the 4.1 ScienceBase state downloads return an HTML page to curl. | US government work. Credit USGS. |
| WA DNR Managed Lands | `https://gis.dnr.wa.gov/site3/rest/services/Public_Boundaries/WADNR_PUBLIC_Managed_Lands/MapServer` | Confirmed | Check the DNR data disclaimer. |
| WA Major Public Lands (non-DNR) | `https://gis.dnr.wa.gov/site3/rest/services/Public_Boundaries/WADNR_PUBLIC_Major_Public_Lands_NonDNR/MapServer` | Confirmed | Same. |
| Oregon PAD-US 2024 copy | `https://services1.arcgis.com/CD5mKowwN6nIaqd8/arcgis/rest/services/library_pln_or_PADUS_2024/FeatureServer/75` | Confirmed. Third-party hosting of PAD-US; the Oregon source for `import-public-lands.sh`. | Derived from PAD-US. |
| PLSS sections | BLM `https://gis.blm.gov/arcgis/rest/services/Cadastral/BLM_Natl_PLSS_CadNSDI/MapServer/2` | Confirmed (WA 74774, OR 104015). The old script queried layer 1, which is the township layer. | US government work. |
| USFS roads | `https://apps.fs.usda.gov/arcx/rest/services/EDW/EDW_RoadBasic_01/MapServer/0` | Confirmed. `EDW_RoadCore_01` is gone. | US government work; not a legal document. |
| USFS trails | `https://apps.fs.usda.gov/arcx/rest/services/EDW/EDW_TrailNFSPublish_01/MapServer/0` | Confirmed. `EDW_TrailNFS_01` is gone. | Same. |
| USFS recreation | `https://apps.fs.usda.gov/arcx/rest/services/EDW/EDW_RecreationOpportunities_01/MapServer/0` | Confirmed. `EDW_RecreationSites_01` is gone. | Same. |
| State outlines | `https://www2.census.gov/geo/tiger/TIGER2023/STATE/tl_2023_us_state.zip` | Confirmed | US government work. |

Sizes depend on the date you download. The USFS layers have no `REGION` field, so the scripts filter on `admin_org` (roads and trails) and `forestorgcode` (recreation) starting with `06`.

## Troubleshooting

**PostGIS won't start.** Run `docker compose logs postgis`. To wipe the database and start over: `docker compose down -v`, then `docker compose up -d`. If compose complains that `PG_PASSWORD` is not set, you have not created `.env`.

**ogr2ogr hangs on an ArcGIS download.** The services can be slow or rate-limit you. Lower `ARCGIS_PAGE_SIZE`, rerun at a quieter time, or use the commented curl fallback in each download script.

**tippecanoe runs out of memory.** Point `--temporary-directory` at a fast disk, process one state at a time (parcels are already split by state), or use a bigger machine.

**Martin shows no tile sources.** Check that `.mbtiles` files are in `backend/tiles/`, read `docker compose logs martin`, and confirm Martin can reach PostGIS with the credentials in `.env`.

**Permission denied on scripts.** Run `chmod +x data-pipeline/*.sh`.
