package com.connorhansen.publiclandmap.presentation.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.connorhansen.publiclandmap.util.CoordinateFormatter

private data class IconOption(val key: String, val label: String, val icon: ImageVector)
private data class ColorOption(val hex: String, val label: String, val color: Color)

private val iconOptions = listOf(
    IconOption("pin", "Pin", Icons.Filled.Place),
    IconOption("flag", "Flag", Icons.Filled.Flag),
    IconOption("camp", "Camp", Icons.Filled.Forest),
    IconOption("star", "Star", Icons.Filled.Star),
    IconOption("parking", "Parking", Icons.Filled.LocalParking),
    IconOption("water", "Water", Icons.Filled.Water),
    IconOption("summit", "Summit", Icons.Filled.Landscape),
    IconOption("viewpoint", "Viewpoint", Icons.Filled.Visibility)
)

private val colorOptions = listOf(
    ColorOption("#FF0000", "Red", Color(0xFFFF0000)),
    ColorOption("#2196F3", "Blue", Color(0xFF2196F3)),
    ColorOption("#4CAF50", "Green", Color(0xFF4CAF50)),
    ColorOption("#FFEB3B", "Yellow", Color(0xFFFFEB3B)),
    ColorOption("#FF9800", "Orange", Color(0xFFFF9800)),
    ColorOption("#9C27B0", "Purple", Color(0xFF9C27B0)),
    ColorOption("#E91E63", "Pink", Color(0xFFE91E63)),
    ColorOption("#009688", "Teal", Color(0xFF009688))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WaypointEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: WaypointsViewModel
) {
    val editState by viewModel.editState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editState.isEditMode) "Edit Waypoint" else "New Waypoint")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetEditState()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editState.isEditMode) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Name field
            OutlinedTextField(
                value = editState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Name *") },
                placeholder = { Text("Waypoint name") },
                isError = editState.nameError != null,
                supportingText = editState.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description field
            OutlinedTextField(
                value = editState.description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text("Description") },
                placeholder = { Text("Optional description") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Coordinates display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewModel.coordinateFormatter.format(
                            editState.latitude,
                            editState.longitude,
                            CoordinateFormatter.Format.DECIMAL_DEGREES
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    editState.elevation?.let { elev ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Elevation: %.0f ft (%.1f m)".format(elev * 3.28084, elev),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Icon picker
            Text(
                text = "Icon",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (option in iconOptions) {
                    val isSelected = editState.icon == option.key
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .then(
                                if (isSelected)
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    )
                                else Modifier
                            )
                            .clickable { viewModel.onIconChange(option.key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.label,
                            modifier = Modifier.size(28.dp),
                            tint = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Color picker
            Text(
                text = "Color",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (option in colorOptions) {
                    val isSelected = editState.color == option.hex
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(option.color)
                            .then(
                                if (isSelected)
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else
                                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                            )
                            .clickable { viewModel.onColorChange(option.hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.resetEditState()
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        viewModel.saveWaypoint {
                            viewModel.resetEditState()
                            onNavigateBack()
                        }
                    },
                    enabled = !editState.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editState.isSaving) "Saving..." else "Save")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Waypoint") },
            text = { Text("Delete \"${editState.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteEditingWaypoint {
                            viewModel.resetEditState()
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
