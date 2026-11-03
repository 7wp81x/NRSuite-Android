package com.swp81x.nrsuite.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Shared expand/collapse motion for module configuration cards. Keeping this
 * in one place prevents some modules from snapping open while others animate.
 */
@Composable
fun NrAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        ) + expandVertically(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        ) + shrinkVertically(
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}
