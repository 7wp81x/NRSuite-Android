package com.swp81x.nrsuite.ui.util

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

fun ClipboardManager.copyWithToast(
    context: Context,
    text: String,
    message: String = "Copied",
) {
    setText(AnnotatedString(text))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
