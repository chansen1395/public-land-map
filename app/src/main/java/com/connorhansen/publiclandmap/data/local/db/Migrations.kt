package com.connorhansen.publiclandmap.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The parcels table only caches API responses, so a schema change drops and recreates it.
 * Waypoints, tracks and offline regions are not touched.
 *
 * Version 5 removed the mailing address column and the owner and address indexes. Version 6
 * removed the site address column. Both migrations end at the version 6 layout, which is
 * the only layout Room checks.
 */
private fun rebuildParcelsCache(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS `parcels`")
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `parcels` (`id` TEXT NOT NULL, `parcel_number` TEXT NOT NULL, " +
            "`owner_name` TEXT, `county` TEXT NOT NULL, `state` TEXT NOT NULL, " +
            "`acreage` REAL, `land_use_code` TEXT, `land_use_desc` TEXT, `centroid_lat` REAL NOT NULL, " +
            "`centroid_lng` REAL NOT NULL, `bbox_min_lat` REAL, `bbox_min_lng` REAL, `bbox_max_lat` REAL, " +
            "`bbox_max_lng` REAL, `geojson` TEXT, `last_updated` INTEGER, PRIMARY KEY(`id`))"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcels_county_state` ON `parcels` (`county`, `state`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcels_parcel_number` ON `parcels` (`parcel_number`)")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_parcels_bbox_min_lat_bbox_max_lat_bbox_min_lng_bbox_max_lng` " +
            "ON `parcels` (`bbox_min_lat`, `bbox_max_lat`, `bbox_min_lng`, `bbox_max_lng`)"
    )
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) = rebuildParcelsCache(db)
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) = rebuildParcelsCache(db)
}
