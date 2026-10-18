package com.swp81x.nrsuite.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.wifi.WifiBand

@Composable
fun WifiBandConfigSection(
    visible: Boolean,
    band: WifiBand,
    fiveGhzAvailable: Boolean,
    fiveGhzUnavailableMessage: String,
    onBandSelected: (WifiBand) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!visible) return
    val context = LocalContext.current

    Column(modifier = modifier) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Band",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        WifiBandSelector(
            selectedBand = band,
            fiveGhzAvailable = fiveGhzAvailable,
            onBandSelected = onBandSelected,
            onUnavailableBandClicked = {
                Toast.makeText(context, fiveGhzUnavailableMessage, Toast.LENGTH_SHORT).show()
            },
            enabled = enabled,
        )
    }
}
