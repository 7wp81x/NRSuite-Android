package com.swp81x.nrsuite

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.swp81x.nrsuite.core.hardware.RootAction
import com.swp81x.nrsuite.core.hardware.RootControlViewModel
import com.swp81x.nrsuite.ui.theme.NRSuiteTheme

class RootControlActivity : ComponentActivity() {
    private lateinit var controls: RootControlViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controls = ViewModelProvider(this)[RootControlViewModel::class.java]
        setContent { NRSuiteTheme { ControlScreen() } }
    }

    private fun openPackage(name: String) {
        val intent = packageManager.getLaunchIntentForPackage(name)
        if (intent == null) Toast.makeText(this, "App not installed or no launcher available: $name", Toast.LENGTH_LONG).show()
        else runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Could not open $name", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ControlScreen() {
        val state by controls.state.collectAsState()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var confirmation by remember { mutableStateOf<RootAction?>(null) }
        fun select(action: RootAction) {
            if (action.effect == null) controls.execute(action) else confirmation = action
        }
        Scaffold(topBar = {
            TopAppBar(title = { Text("Root / NetHunter") }, navigationIcon = {
                TextButton(onClick = { finish() }) { Text("Back") }
            })
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    Text(if (state.rootGranted) "Root granted for this session" else "Root has not been granted to this app")
                    Button(onClick = { controls.execute(RootAction.ROOT_STATUS) }, enabled = !state.busy) { Text("Check root") }
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Native radios") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("NetHunter") })
                    }
                }
                if (tab == 0) {
                    item {
                        Text("Control Android radios with root. Discovery remains available in Phone without root.")
                        Text("Driver capability reports do not prove working monitor capture or injection.", style = MaterialTheme.typography.bodySmall)
                    }
                    for (actions in listOf(
                        listOf(RootAction.RADIO_STATUS, RootAction.WIFI_CAPABILITIES),
                        listOf(RootAction.WIFI_ON, RootAction.WIFI_OFF),
                        listOf(RootAction.BLUETOOTH_ON, RootAction.BLUETOOTH_OFF),
                        listOf(RootAction.NFC_ON, RootAction.NFC_OFF))) {
                        item { ActionRow(actions, state.busy, state.rootGranted, ::select) }
                    }
                } else {
                    item {
                        Text("Uses the installed NetHunter chroot. Service controls use its existing configuration.")
                        Text("Stop idle chroot refuses while Kali processes are active. Stop services and close terminals first.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openPackage("com.offsec.nethunter") }) { Text("NetHunter app") }
                            TextButton(onClick = { openPackage("com.offsec.nhterm") }) { Text("Terminal") }
                            TextButton(onClick = { openPackage("com.offsec.nethunter.kex") }) { Text("KeX") }
                        }
                    }
                    for (actions in listOf(
                        listOf(RootAction.KALI_HEALTH, RootAction.KALI_INTERFACES),
                        listOf(RootAction.KALI_TOOLS),
                        listOf(RootAction.KALI_NMAP_VERSION, RootAction.KALI_TCPDUMP_VERSION),
                        listOf(RootAction.CHROOT_START, RootAction.CHROOT_STOP),
                        listOf(RootAction.SSH_STATUS, RootAction.SSH_START, RootAction.SSH_STOP),
                        listOf(RootAction.APACHE_STATUS, RootAction.APACHE_START, RootAction.APACHE_STOP),
                        listOf(RootAction.POSTGRES_STATUS, RootAction.POSTGRES_START, RootAction.POSTGRES_STOP))) {
                        item { ActionRow(actions, state.busy, state.rootGranted, ::select) }
                    }
                }
                item {
                    HorizontalDivider()
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Command result", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer { Text(state.output, style = MaterialTheme.typography.bodySmall) }
                    Text("Results stay in memory. Exit status is shown; refresh the relevant status after changing a radio or service.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        confirmation?.let { action ->
            AlertDialog(onDismissRequest = { confirmation = null },
                title = { Text(action.label) }, text = { Text(action.effect.orEmpty()) },
                confirmButton = { TextButton(onClick = { confirmation = null; controls.execute(action) }) { Text("Run") } },
                dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun ActionRow(actions: List<RootAction>, busy: Boolean, root: Boolean, select: (RootAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            OutlinedButton(onClick = { select(action) }, enabled = !busy && root, modifier = Modifier.weight(1f)) {
                Text(action.label)
            }
        }
    }
}
