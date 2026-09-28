package com.swp81x.nrsuite.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SavedScriptPicker(
    names: List<String>,
    selectedName: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
    label: String = "Saved scripts",
    modifier: Modifier = Modifier,
) {
    SavedItemDropdown(
        label = label,
        selectedName = selectedName,
        names = names,
        enabled = enabled,
        onSelect = onSelect,
        modifier = modifier,
    )
}
