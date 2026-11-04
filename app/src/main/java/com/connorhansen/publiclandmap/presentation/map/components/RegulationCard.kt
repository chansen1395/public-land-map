package com.connorhansen.publiclandmap.presentation.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connorhansen.publiclandmap.domain.model.Regulation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RegulationCard(
    regulation: Regulation,
    modifier: Modifier = Modifier
) {
    val typeInfo = regulationTypeInfo(regulation.regulationType)
    val restrictionColor = restrictionLevelColor(regulation.restrictionLevel)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = restrictionColor.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, restrictionColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = typeInfo.icon,
                    contentDescription = typeInfo.label,
                    tint = restrictionColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = regulation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = restrictionColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Restriction level badge
            if (!regulation.restrictionLevel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatRestrictionLevel(regulation.restrictionLevel),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = restrictionColor
                )
            }

            // Description
            if (!regulation.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = regulation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            // Date range
            val dateRange = formatDateRange(regulation.startDate, regulation.endDate)
            if (dateRange != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dateRange,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private data class RegulationTypeInfo(
    val icon: ImageVector,
    val label: String
)

private fun regulationTypeInfo(type: String): RegulationTypeInfo {
    val normalized = type.lowercase()
    return when {
        "fire" in normalized -> RegulationTypeInfo(Icons.Filled.LocalFireDepartment, "Fire Restriction")
        "camp" in normalized -> RegulationTypeInfo(Icons.Filled.NightsStay, "Camping")
        "motor" in normalized || "vehicle" in normalized -> RegulationTypeInfo(Icons.Filled.DirectionsCar, "Motor Vehicle")
        "closure" in normalized || "closed" in normalized -> RegulationTypeInfo(Icons.Filled.Warning, "Area Closure")
        "ohv" in normalized || "off-highway" in normalized || "atv" in normalized -> RegulationTypeInfo(Icons.Filled.SportsMotorsports, "OHV")
        "fish" in normalized -> RegulationTypeInfo(Icons.Filled.Phishing, "Fishing")
        else -> RegulationTypeInfo(Icons.Filled.Info, "Regulation")
    }
}

private fun restrictionLevelColor(level: String?): Color {
    if (level.isNullOrBlank()) return Color(0xFF1976D2) // default blue
    val normalized = level.lowercase()
    return when {
        "none" in normalized || "open" in normalized -> Color(0xFF388E3C) // green
        "stage 1" in normalized || "stage1" in normalized || "moderate" in normalized -> Color(0xFFF9A825) // yellow
        "stage 2" in normalized || "stage2" in normalized || "high" in normalized -> Color(0xFFEF6C00) // orange
        "stage 3" in normalized || "total" in normalized || "extreme" in normalized || "closed" in normalized -> Color(0xFFC62828) // red
        else -> Color(0xFF1976D2) // blue
    }
}

private fun formatRestrictionLevel(level: String): String {
    return level.replaceFirstChar { it.uppercase() }
}

private fun formatDateRange(startDate: String?, endDate: String?): String? {
    if (startDate.isNullOrBlank() && endDate.isNullOrBlank()) return null

    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    val start = startDate?.let {
        try {
            LocalDate.parse(it).format(formatter)
        } catch (_: Exception) {
            it
        }
    }
    val end = endDate?.let {
        try {
            LocalDate.parse(it).format(formatter)
        } catch (_: Exception) {
            it
        }
    }

    return when {
        start != null && end != null -> "Effective: $start – $end"
        start != null -> "Effective from: $start"
        end != null -> "Until: $end"
        else -> null
    }
}
