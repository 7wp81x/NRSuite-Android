package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber

/**
 * Controls best-effort plaintext custom-DNS interception. DHCP DNS, wildcard
 * DNS, and forced portal routes remain active when this is disabled.
 */
@Composable
fun PortalDnsHijackToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Intercept custom DNS",
                style = MaterialTheme.typography.bodyMedium,
                color = NrOnSurface,
            )
            Text(
                text = if (enabled) {
                    "Plaintext external DNS is redirected to 192.168.4.1."
                } else {
                    "Disabled for this app run. DHCP DNS, wildcard DNS, and forced routes still work."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) NrOnSurfaceVariant else StatusAmber,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}
