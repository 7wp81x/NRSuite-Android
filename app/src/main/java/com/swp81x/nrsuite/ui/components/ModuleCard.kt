package com.swp81x.nrsuite.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusGreen

data class ModuleCardSpec(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: String = "General",
    val radio: String = "",
    val iconTint: Color? = null,
    val available: Boolean = true,
    val statusLabel: String? = null,
    val statusColor: Color? = null,
    val isRunning: Boolean = false,
)

@Composable
fun ModuleCard(
    module: ModuleCardSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel = module.statusLabel?.takeIf { it.isNotBlank() }
    val attentionLabel = statusLabel?.takeIf {
        it in setOf("Firmware required", "Not supported", "OUI DB required", "Unavailable")
    }
    val deviceNeededLabel = statusLabel?.takeIf { it == "Device needed" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = module.available) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (module.available) NrSurface else NrSurfaceVariant.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 0.5.dp,
            color = if (module.available) NrOutline else NrOutline.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (module.available) 1f else 0.55f)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = module.iconTint ?: NrAccent
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (module.available) tint.copy(alpha = 0.12f) else NrSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = module.icon,
                    contentDescription = null,
                    tint = if (module.available) tint else NrOnSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.title,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (attentionLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = attentionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(
                                    color = module.statusColor ?: NrOnSurfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.size(3.dp))
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                if (module.radio.isNotBlank() || deviceNeededLabel != null) {
                    Spacer(Modifier.size(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (module.radio.isNotBlank()) {
                            Text(
                                text = module.radio,
                                style = MaterialTheme.typography.labelSmall,
                                color = NrOnSurfaceVariant,
                                modifier = Modifier
                                    .border(0.5.dp, NrOutline, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (deviceNeededLabel != null) {
                            if (module.radio.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = deviceNeededLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = NrOnSurfaceVariant,
                                modifier = Modifier
                                    .border(0.5.dp, NrOutline, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (module.isRunning) {
                val transition = rememberInfiniteTransition(label = "module-running")
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "module-running-alpha",
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(StatusGreen.copy(alpha = alpha), CircleShape),
                )
            }
        }
    }
}
