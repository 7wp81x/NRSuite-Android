package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen

@Composable
fun HtmlUploadSection(
    selectedName: String?,
    uploading: Boolean,
    progress: Int,
    completed: Boolean,
    required: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Custom HTML",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))

        val displayName = when {
            selectedName != null -> selectedName
            required -> "No HTML file selected — required"
            else -> "No file selected — device placeholder page"
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = when {
                selectedName != null -> NrOnSurfaceVariant
                required -> StatusAmber
                else -> NrOnSurfaceVariant
            },
        )

        when {
            uploading -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Uploading HTML... $progress%",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
            selectedName != null && completed -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "✓ HTML ready  ·  $selectedName",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = StatusGreen,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onChoose,
                enabled = enabled && !uploading,
            ) {
                Text(if (selectedName == null) "Choose HTML" else "Change HTML")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = enabled && !uploading && selectedName != null,
            ) {
                Text("Clear")
            }
        }
    }
}
