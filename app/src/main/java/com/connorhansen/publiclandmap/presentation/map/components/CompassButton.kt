package com.connorhansen.publiclandmap.presentation.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

@Composable
fun CompassButton(
    bearing: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = bearing != 0.0
    val rotation by animateFloatAsState(
        targetValue = -bearing.toFloat(),
        label = "compass_rotation"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Navigation,
                contentDescription = "Reset compass bearing to north",
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
