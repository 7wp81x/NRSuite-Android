package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.ir.IrCommonButton
import com.swp81x.nrsuite.core.ir.IrCommonButtonsState
import com.swp81x.nrsuite.core.ir.IrRemoteSummary
import com.swp81x.nrsuite.core.ir.IrSearchCandidate
import com.swp81x.nrsuite.core.ir.IrSearchRunState
import com.swp81x.nrsuite.core.ir.IrSearchUiState
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.categoryColor
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun IrSearchScreen(
    remotes: List<IrRemoteSummary>,
    state: IrSearchUiState,
    commonButtonsState: IrCommonButtonsState,
    initialCategory: String,
    initialBrand: String,
    onSelectionChanged: (category: String, brand: String) -> Unit,
    onPrepareCommonButtons: (category: String, brand: String, minRemotes: Int) -> Unit,
    onRefreshCommonButtons: (category: String, brand: String, minRemotes: Int) -> Unit,
    onStart: (category: String, brand: String, selectedButtons: List<String>, intervalMs: Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onManualSend: () -> Unit,
    onToggleLoop: () -> Unit,
    onStop: () -> Unit,
    onFound: () -> Unit,
    onOpenMarkedRemote: (IrSearchCandidate) -> Unit,
    onRemoveMarkedResult: (IrSearchCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var brand by rememberSaveable { mutableStateOf(initialBrand) }
    var selectedButtons by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var intervalMs by rememberSaveable { mutableIntStateOf(1200) }
    var commonFilter by rememberSaveable { mutableStateOf("") }
    var commonMinRemotes by rememberSaveable { mutableIntStateOf(10) }
    var categoryMenu by remember { mutableStateOf(false) }
    var brandMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }

    val categories = remember(remotes) {
        remotes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val brands = remember(remotes, category) {
        val categoryRemotes = remotes.filter { category.isNotBlank() && it.category == category }
        val counts = categoryRemotes
            .map { it.brand }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
        val list = mutableListOf("All brands")
        list += counts.entries.sortedBy { it.key.lowercase() }.map { it.key }
        list to counts
    }
    val brandOptions = brands.first
    val brandCounts = brands.second
    val active = state.runState == IrSearchRunState.RUNNING ||
        state.runState == IrSearchRunState.PAUSED

    LaunchedEffect(category, brand) {
        onSelectionChanged(category, brand)
    }
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && category.isNotBlank() && category !in categories) {
            category = ""
            brand = "All brands"
        }
    }
    LaunchedEffect(brandOptions, remotes.isNotEmpty()) {
        if (remotes.isNotEmpty() && brand !in brandOptions) {
            brand = "All brands"
        }
    }

    LaunchedEffect(category, brand, commonMinRemotes) {
        if (category.isNotBlank()) {
            onPrepareCommonButtons(category, brand, commonMinRemotes)
        }
    }
    LaunchedEffect(category, brand) {
        selectedButtons = emptySet()
    }
    LaunchedEffect(
        commonButtonsState.loaded,
        commonButtonsState.minRemotes,
        commonButtonsState.buttons,
    ) {
        if (commonButtonsState.loaded && commonButtonsState.minRemotes == commonMinRemotes) {
            val allowed = commonButtonsState.buttons.map { it.name }.toSet()
            selectedButtons = selectedButtons.intersect(allowed)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "IR Finder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Authorized testing only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

                Text("Category", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !active,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NrOnSurfaceVariant,
                        ),
                    ) {
                        Text(category.ifBlank { "Select category" }, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose category")
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    category = item
                                    brand = "All brands"
                                    categoryMenu = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Brand", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { brandMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !active,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NrOnSurfaceVariant,
                        ),
                    ) {
                        Text(
                            text = if (brand == "All brands") {
                                "All brands"
                            } else {
                                "$brand (${brandCounts[brand] ?: 0})"
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose brand")
                    }
                    DropdownMenu(expanded = brandMenu, onDismissRequest = { brandMenu = false }) {
                        brandOptions.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (item == "All brands") item else {
                                            "$item (${brandCounts[item] ?: 0})"
                                        },
                                    )
                                },
                                onClick = {
                                    brand = item
                                    brandMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!active && category.isNotBlank()) {
            CommonButtonsCard(
                state = commonButtonsState,
                selectedButtons = selectedButtons,
                enabled = !active,
                filter = commonFilter,
                minRemotes = commonMinRemotes,
                onFilterChange = { commonFilter = it },
                onMinRemotesChange = { commonMinRemotes = it },
                onToggle = { button ->
                    selectedButtons = if (button.name in selectedButtons) {
                        selectedButtons - button.name
                    } else {
                        selectedButtons + button.name
                    }
                },
                onRefresh = { onRefreshCommonButtons(category, brand, commonMinRemotes) },
            )
        }

        if (!active) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Search settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Auto-step / loop interval", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { intervalMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !active,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NrOnSurfaceVariant,
                        ),
                    ) {
                        Text("$intervalMs ms", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose interval")
                    }
                    DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                        listOf(500, 750, 1000, 1200, 1500, 2000, 3000, 5000).forEach { item ->
                            DropdownMenuItem(
                                text = { Text("$item ms") },
                                onClick = {
                                    intervalMs = item
                                    intervalMenu = false
                                },
                            )
                        }
                    }
                }
                if (selectedButtons.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Selected: " + selectedButtons.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onStart(category, brand, selectedButtons.toList(), intervalMs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !active &&
                        category.isNotBlank() &&
                        commonButtonsState.loaded &&
                        selectedButtons.isNotEmpty() &&
                        !commonButtonsState.loading,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start Searcher")
                }
            }
        }
        }

        if (active && state.candidates.isNotEmpty()) {
            SearchCandidateCard(
                state = state,
                onPrevious = onPrevious,
                onNext = onNext,
                onManualSend = onManualSend,
                onPause = onPause,
                onResume = onResume,
                onToggleLoop = onToggleLoop,
                onStop = onStop,
                onFound = onFound,
                onOpenCurrent = onOpenMarkedRemote,
            )
        }

        if (state.foundCandidates.isNotEmpty()) {
            FoundCandidatesCard(
                found = state.foundCandidates,
                onOpen = onOpenMarkedRemote,
                onRemove = onRemoveMarkedResult,
            )
        }

        if (state.runState == IrSearchRunState.COMPLETED ||
            state.runState == IrSearchRunState.STOPPED ||
            state.runState == IrSearchRunState.ERROR
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = when (state.runState) {
                            IrSearchRunState.COMPLETED -> "Search finished"
                            IrSearchRunState.STOPPED -> "Search stopped"
                            IrSearchRunState.ERROR -> "Search error"
                            else -> "Search status"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.errorMessage ?: state.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CommonButtonsCard(
    state: IrCommonButtonsState,
    selectedButtons: Set<String>,
    enabled: Boolean,
    filter: String,
    minRemotes: Int,
    onFilterChange: (String) -> Unit,
    onMinRemotesChange: (Int) -> Unit,
    onToggle: (IrCommonButton) -> Unit,
    onRefresh: () -> Unit,
) {
    var thresholdMenu by remember { mutableStateOf(false) }
    val irAccent = categoryColor("IR")
    val needle = filter.trim()
    val filtered = (if (needle.isBlank()) {
        state.buttons
    } else {
        state.buttons.filter { it.name.contains(needle, ignoreCase = true) }
    }).sortedWith(
        compareByDescending<IrCommonButton> { it.name in selectedButtons }
            .thenByDescending { it.count }
            .thenBy { it.name.lowercase() },
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Common buttons",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Buttons appearing in ${minRemotes}+ remotes",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onRefresh, enabled = enabled && !state.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }

            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { thresholdMenu = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NrOnSurfaceVariant,
                    ),
                ) {
                    Text(
                        text = "Minimum remote occurrences: ${minRemotes}+",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose threshold")
                }
                DropdownMenu(
                    expanded = thresholdMenu,
                    onDismissRequest = { thresholdMenu = false },
                ) {
                    listOf(3, 5, 10, 20, 50).forEach { value ->
                        DropdownMenuItem(
                            text = { Text("$value+ remotes") },
                            onClick = {
                                onMinRemotesChange(value)
                                thresholdMenu = false
                            },
                        )
                    }
                }
            }
            Text(
                text = "Lower thresholds expose more buttons and can create broader searches.",
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
            )

            if (state.loading) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Building common-button list...", style = MaterialTheme.typography.bodySmall)
                }
                return@Column
            }

            if (!state.loaded) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No cached common buttons for ${state.category} · ${state.brand}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRefresh, enabled = enabled) {
                    Text("Load common buttons")
                }
                return@Column
            }

            if (state.buttons.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No buttons with ${minRemotes}+ occurrences were found for this scope.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = onFilterChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter common buttons") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = irAccent,
                    focusedLabelColor = irAccent,
                    cursorColor = irAccent,
                ),
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { onFilterChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.name }) { button ->
                        FilterChip(
                            selected = button.name in selectedButtons,
                            onClick = { onToggle(button) },
                            enabled = enabled,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = NrSurfaceVariant,
                                labelColor = NrOnSurfaceVariant,
                                selectedContainerColor = NrOnSurfaceVariant.copy(alpha = 0.28f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            label = { Text("${button.name} (${button.count})", maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IrSearchTransmitIndicator(state: IrSearchUiState) {
    val transmitting = state.runState == IrSearchRunState.RUNNING &&
        (state.autoStepping || state.loopingCurrent)
    val looping = transmitting && state.loopingCurrent
    val label = when {
        looping -> "LOOPING"
        transmitting -> "SENDING"
        state.runState == IrSearchRunState.PAUSED -> "PAUSED"
        else -> "IDLE"
    }
    val color = if (transmitting) StatusGreen else StatusNeutral

    val transition = rememberInfiniteTransition(label = "ir-search-tx")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ir-search-tx-alpha",
    )
    val dotAlpha = if (transmitting) alpha else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color.copy(alpha = dotAlpha), CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun FoundCandidatesCard(
    found: List<IrSearchCandidate>,
    onOpen: (IrSearchCandidate) -> Unit,
    onRemove: (IrSearchCandidate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Saved results",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusGreen,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = found.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier
                        .background(NrSurface, RoundedCornerShape(50))
                        .border(0.5.dp, NrOutline, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 230.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(
                    items = found,
                    key = { "${it.remoteId}:${it.buttonName}" },
                ) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NrSurface, RoundedCornerShape(8.dp))
                            .border(0.5.dp, NrOutline, RoundedCornerShape(8.dp))
                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.remoteName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = candidate.buttonName.replace('_', ' ') +
                                    candidate.brand.takeIf { it.isNotBlank() }
                                        ?.let { " · $it" }
                                        .orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = NrOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onOpen(candidate) }) { Text("Open") }
                        TextButton(onClick = { onRemove(candidate) }) { Text("Remove") }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Open pauses Finder temporarily and opens the remote for verification. " +
                    "Back returns here paused.",
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchCandidateCard(
    state: IrSearchUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onManualSend: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onToggleLoop: () -> Unit,
    onStop: () -> Unit,
    onFound: () -> Unit,
    onOpenCurrent: (IrSearchCandidate) -> Unit,
) {
    val candidate = state.currentCandidate
    var confirmStop by remember { mutableStateOf(false) }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop searching?") },
            text = { Text("This ends the search and returns to the main screen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        onStop()
                    },
                ) { Text("Stop", color = StatusRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("Keep searching") }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Candidate ${(state.currentIndex + 1).coerceAtMost(state.candidates.size)} " +
                        "of ${state.candidates.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IrSearchTransmitIndicator(state = state)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = {
                    if (state.candidates.isEmpty()) {
                        0f
                    } else {
                        ((state.currentIndex + 1).toFloat() / state.candidates.size).coerceIn(0f, 1f)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = candidate?.remoteName ?: "Finishing...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = listOfNotNull(
                    candidate?.brand?.takeIf { it.isNotBlank() },
                    candidate?.buttonName?.replace('_', ' '),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Auto step: ${if (state.autoStepping) "ON" else "OFF"} · " +
                    "Loop current: ${if (state.loopingCurrent) "ON" else "OFF"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.loopingCurrent) StatusGreen else NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // Row 1: navigation + main action (Send is the anchor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }
                Button(
                    onClick = onManualSend,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                ) {
                    Text("Send", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onNext,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
            Spacer(Modifier.height(8.dp))

            // Row 2: loop mode toggle
            OutlinedButton(
                onClick = onToggleLoop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp),
                enabled = candidate != null,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (state.loopingCurrent) StatusGreen else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.loopingCurrent) "Loop signal · ON" else "Loop signal")
            }
            Spacer(Modifier.height(8.dp))

            // Row 3: Pause/Resume next to Save (the usual react -> pause -> save flow)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.autoStepping) {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier.heightIn(min = 42.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber),
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pause", maxLines = 1, softWrap = false)
                    }
                } else {
                    OutlinedButton(
                        onClick = onResume,
                        modifier = Modifier.heightIn(min = 42.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Resume", maxLines = 1, softWrap = false)
                    }
                }
                Button(
                    onClick = onFound,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                    enabled = candidate != null,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Row 4: open the current candidate's remote (works without saving)
            OutlinedButton(
                onClick = { candidate?.let(onOpenCurrent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp),
                enabled = candidate != null,
            ) {
                Text("Open remote")
            }

            // Stop is rare + ends the session, so it's isolated and confirmed
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { confirmStop = true },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = StatusRed),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stop search")
            }
        }
    }
}
