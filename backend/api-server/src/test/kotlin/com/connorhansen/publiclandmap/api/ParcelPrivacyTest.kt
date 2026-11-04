package com.connorhansen.publiclandmap.api

import com.connorhansen.publiclandmap.api.model.Parcel
import com.connorhansen.publiclandmap.api.repository.ParcelRepository
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards for what parcel data the API exposes. Owner names are shown as the county records
 * them, but they are not searchable. Neither mailing addresses nor site addresses are stored
 * or returned: an owner name next to a site address would amount to a home address.
 */
class ParcelPrivacyTest {

    private val parcel = Parcel(
        id = "wa-1",
        parcelNumber = "000-000-0000",
        ownerName = "EXAMPLE OWNER LLC",
        county = "Example",
        state = "WA",
        centroidLat = 46.85,
        centroidLng = -121.76
    )

    private val addressTerms = listOf("mailing", "situs", "address")

    @Test
    fun parcelJsonHasNoAddressFields() {
        val text = Json { encodeDefaults = true }.encodeToString(Parcel.serializer(), parcel)
        for (term in addressTerms) {
            assertFalse(text.contains(term, ignoreCase = true), "parcel JSON must not carry '$term': $text")
        }
        assertTrue(text.contains("\"owner_name\":\"EXAMPLE OWNER LLC\""))
        assertTrue(text.contains("\"parcel_number\":\"000-000-0000\""))
    }

    @Test
    fun parcelModelHasNoAddressProperty() {
        val descriptor = Parcel.serializer().descriptor
        val names = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        for (term in addressTerms) {
            assertTrue(names.none { it.contains(term, ignoreCase = true) }, "Parcel must not have a '$term' field: $names")
        }
        assertTrue("owner_name" in names)
    }

    @Test
    fun parcelSearchMatchesTheParcelNumberOnly() {
        assertEquals("parcel_number ILIKE ?", ParcelRepository.SEARCH_FILTER)
    }

    @Test
    fun schemaHasNoAddressColumnsAndNoOwnerIndex() {
        // Gradle runs tests from the project directory, backend/api-server
        val sql = File("../sql/init.sql").readText()
        val parcelsTable = sql.substringAfter("CREATE TABLE parcels (").substringBefore(");")
        for (term in addressTerms) {
            assertFalse(parcelsTable.contains(term, ignoreCase = true), "parcels table must not have '$term': $parcelsTable")
        }
        assertFalse(sql.contains("idx_parcels_owner"))
        assertFalse(sql.contains("idx_parcels_address"))
    }
}
