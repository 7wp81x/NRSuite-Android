package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ChannelStepper(
    value: Int,
    min: Int = 1,
    max: Int = 13,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onDecrease,
            enabled = enabled && value > min,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease channel",
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(44.dp)
                .padding(horizontal = 8.dp),
        )
        IconButton(
            onClick = onIncrease,
            enabled = enabled && value < max,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase channel",
            )
        }
    }
}
