package com.connorhansen.publiclandmap.presentation.map.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connorhansen.publiclandmap.domain.model.PublicLand
import com.connorhansen.publiclandmap.domain.model.Regulation
import com.connorhansen.publiclandmap.presentation.map.MapViewModel
import com.connorhansen.publiclandmap.util.PublicLandColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicLandSheet(
    feature: MapViewModel.SelectedFeature.PublicLand,
    publicLand: PublicLand?,
    regulations: List<Regulation>,
    isLoading: Boolean,
    onSaveWaypoint: () -> Unit,
    onDirections: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        val props = feature.properties
        val agencyName = publicLand?.managingAgency ?: props["managing_agency"] ?: "Unknown"
        val designationType = publicLand?.designationType ?: props["designation_type"]
        val publicAccess = publicLand?.publicAccess ?: props["public_access"] ?: "Unknown"
        val agencyCategory = PublicLandColors.getCategory(
            managingAgency = agencyName,
            agencyCategory = publicLand?.agencyCategory ?: props["agency_category"],
            designationType = designationType,
            publicAccess = publicAccess
        )
        val agencyColor = PublicLandColors.getColor(
            managingAgency = agencyName,
            agencyCategory = agencyCategory,
            designationType = designationType,
            publicAccess = publicAccess
        )
        val unitName = publicLand?.unitName ?: props["unit_name"] ?: props["name"] ?: "Public Land"

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with agency color indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    drawCircle(color = agencyColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = unitName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = agencyName,
                style = MaterialTheme.typography.titleMedium,
                color = agencyColor
            )

            Text(
                text = agencyCategory,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Detail properties
            PropertyRow(
                "Category",
                agencyCategory
            )
            PropertyRow(
                "Agency",
                publicLand?.managingAgency ?: props["managing_agency"]
            )
            PropertyRow(
                "Type",
                designationType
            )
            PropertyRow(
                "Access",
                publicAccess
            )
            PropertyRow(
                "Acreage",
                (publicLand?.gisAcres?.let { formatAcreage(it) })
                    ?: props["gis_acres"]?.let { formatAcreageStr(it) }
            )
            PropertyRow(
                "State",
                publicLand?.state ?: props["state_name"] ?: props["state"]
            )

            // Loading indicator for details
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            // Regulations section
            if (regulations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "REGULATIONS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                regulations.forEach { regulation ->
                    RegulationCard(
                        regulation = regulation,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Action buttons
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSaveWaypoint,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Waypoint")
                }

                OutlinedButton(
                    onClick = onDirections,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Directions")
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatAcreage(acres: Double): String {
    return when {
        acres >= 1000 -> String.format("%,.0f ac", acres)
        acres >= 1 -> String.format("%.1f ac", acres)
        else -> String.format("%.2f ac", acres)
    }
}

private fun formatAcreageStr(acres: String): String {
    return try {
        formatAcreage(acres.toDouble())
    } catch (_: NumberFormatException) {
        acres
    }
}
