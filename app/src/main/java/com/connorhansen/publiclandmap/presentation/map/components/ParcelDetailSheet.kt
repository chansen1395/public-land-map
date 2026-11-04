package com.connorhansen.publiclandmap.presentation.map.components

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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connorhansen.publiclandmap.domain.model.Parcel
import com.connorhansen.publiclandmap.presentation.map.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelDetailSheet(
    feature: MapViewModel.SelectedFeature.Parcel,
    parcel: Parcel?,
    isLoading: Boolean,
    onSaveWaypoint: () -> Unit,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parcel Detail",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            // Property rows - prefer domain model, fallback to tile properties
            ParcelPropertyRow(
                "Owner",
                parcel?.ownerName ?: props["owner_name"]
            )
            ParcelPropertyRow(
                "Parcel #",
                parcel?.parcelNumber ?: props["parcel_number"]
            )
            ParcelPropertyRow(
                "County",
                buildCountyState(
                    parcel?.county ?: props["county"],
                    parcel?.state ?: props["state"]
                )
            )
            ParcelPropertyRow(
                "Acreage",
                (parcel?.acreage?.let { formatParcelAcreage(it) })
                    ?: props["acreage"]?.let { formatParcelAcreageStr(it) }
            )
            ParcelPropertyRow(
                "Assessor Use",
                parcel?.landUseDesc ?: props["land_use"] ?: props["land_use_desc"]
            )
            ParcelPropertyRow(
                "Zoning",
                props["zoning"]
            )

            // Action buttons
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onSaveWaypoint,
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Waypoint")
                }
            }
        }
    }
}

@Composable
private fun ParcelPropertyRow(label: String, value: String?) {
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

private fun buildCountyState(county: String?, state: String?): String? {
    val parts = listOfNotNull(county, state).filter { it.isNotBlank() }
    return parts.joinToString(", ").ifBlank { null }
}

private fun formatParcelAcreage(acres: Double): String {
    return when {
        acres >= 1000 -> String.format("%,.0f ac", acres)
        acres >= 1 -> String.format("%.1f ac", acres)
        else -> String.format("%.2f ac", acres)
    }
}

private fun formatParcelAcreageStr(acres: String): String {
    return try {
        formatParcelAcreage(acres.toDouble())
    } catch (_: NumberFormatException) {
        acres
    }
}
