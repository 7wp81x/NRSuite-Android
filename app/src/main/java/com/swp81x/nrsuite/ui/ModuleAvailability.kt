package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral

internal val LocalModuleUnavailableMessage = compositionLocalOf<String?> { null }

@Composable
internal fun ModuleScreenScaffold(
    unavailableMessage: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalModuleUnavailableMessage provides unavailableMessage) {
        Column(modifier = modifier.fillMaxSize()) {
            ModuleAvailabilityBanner()
            Box(Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
private fun ModuleAvailabilityBanner() {
    val message = LocalModuleUnavailableMessage.current?.takeIf { it.isNotBlank() } ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = StatusAmber,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ModuleActionFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable () -> Unit,
) {
    val unavailableMessage = LocalModuleUnavailableMessage.current?.takeIf { it.isNotBlank() }
    val context = LocalContext.current

    FloatingActionButton(
        onClick = {
            when {
                !enabled -> Unit
                unavailableMessage != null -> {
                    Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
                }
                else -> onClick()
            }
        },
        modifier = modifier.alpha(
            when {
                !enabled -> 0.4f
                unavailableMessage != null -> 0.55f
                else -> 1f
            },
        ),
        containerColor = if (unavailableMessage != null) StatusNeutral else containerColor,
        contentColor = if (unavailableMessage != null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            contentColor
        },
        content = content,
    )
}
