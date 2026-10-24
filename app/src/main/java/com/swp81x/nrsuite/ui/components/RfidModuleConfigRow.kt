package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant

@Composable
fun RfidModuleConfigRow(
    interfaceLabel: String,
    sdaLabel: String,
    sclLabel: String,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RfidInfoChip(interfaceLabel)
        RfidInfoChip(sdaLabel)
        RfidInfoChip(sclLabel)
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onOpenConfig,
            colors = ButtonDefaults.textButtonColors(contentColor = NrOnSurfaceVariant),
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accentColor ?: MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "RFID Config",
                color = NrOnSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RfidInfoChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        fontWeight = FontWeight.Medium,
        color = NrOnSurfaceVariant,
        modifier = Modifier
            .background(NrSurfaceVariant, RoundedCornerShape(7.dp))
            .border(0.5.dp, NrOutline, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
