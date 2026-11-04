package com.connorhansen.publiclandmap.presentation.map.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connorhansen.publiclandmap.presentation.map.MapViewModel
import com.connorhansen.publiclandmap.util.PublicLandColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerPanel(
    selectedBasemap: MapViewModel.Basemap,
    visibleLayers: Set<MapViewModel.MapLayer>,
    onBasemapSelected: (MapViewModel.Basemap) -> Unit,
    onLayerToggled: (MapViewModel.MapLayer) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Map Layers",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Basemap section
            SectionHeader("Basemap")
            MapViewModel.Basemap.entries.forEach { basemap ->
                BasemapRow(
                    basemap = basemap,
                    isSelected = basemap == selectedBasemap,
                    onSelect = { onBasemapSelected(basemap) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Layer categories
            MapViewModel.LayerCategory.entries.forEach { category ->
                SectionHeader(category.title)
                MapViewModel.MapLayer.entries
                    .filter { it.category == category }
                    .forEach { layer ->
                        LayerRow(
                            layer = layer,
                            isChecked = layer in visibleLayers,
                            onToggle = { onLayerToggled(layer) }
                        )
                    }
                if (category == MapViewModel.LayerCategory.LAND_OVERLAYS && MapViewModel.MapLayer.PUBLIC_LANDS in visibleLayers) {
                    PublicLandLegend(modifier = Modifier.padding(start = 48.dp, top = 6.dp, bottom = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PublicLandLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "PUBLIC LAND COLORS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        PublicLandColors.legendItems.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(color = item.color)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun BasemapRow(
    basemap: MapViewModel.Basemap,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = basemap.title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun LayerRow(
    layer: MapViewModel.MapLayer,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isChecked,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = layer.title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
