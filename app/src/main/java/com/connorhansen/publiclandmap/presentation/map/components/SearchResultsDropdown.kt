package com.connorhansen.publiclandmap.presentation.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.connorhansen.publiclandmap.presentation.map.SearchResult

@Composable
fun SearchResultsDropdown(
    results: List<SearchResult>,
    isSearching: Boolean,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isSearching && results.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Searching…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        LazyColumn {
            items(results) { result ->
                SearchResultRow(
                    result = result,
                    onClick = { onResultClick(result) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (result) {
            is SearchResult.PublicLandResult -> Icons.Filled.Forest to MaterialTheme.colorScheme.primary
            is SearchResult.ParcelResult -> Icons.Filled.Map to MaterialTheme.colorScheme.tertiary
            is SearchResult.PlaceResult -> Icons.Filled.LocationOn to MaterialTheme.colorScheme.secondary
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (result) {
                    is SearchResult.PublicLandResult -> result.publicLand.unitName
                    is SearchResult.ParcelResult -> result.parcel.parcelNumber
                    is SearchResult.PlaceResult -> result.name
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (result) {
                    is SearchResult.PublicLandResult -> {
                        val pl = result.publicLand
                        listOfNotNull(pl.managingAgency, pl.designationType, pl.state)
                            .joinToString(" · ")
                    }
                    is SearchResult.ParcelResult -> {
                        val p = result.parcel
                        listOfNotNull(p.county, p.state)
                            .joinToString(" · ")
                    }
                    is SearchResult.PlaceResult -> result.displayName
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
