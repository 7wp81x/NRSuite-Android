package com.swp81x.nrsuite.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

internal data class TreeUriSelection(
    val uri: Uri,
    val grantFlags: Int,
)

/**
 * Captures the actual grants returned by the system folder picker.
 *
 * OpenDocumentTree() only returns the URI, which forces callers to guess which
 * persistable permissions were granted. Some OEM providers grant a different
 * subset of read/write access, so retain the result flags and persist exactly
 * those grants.
 */
internal class OpenDocumentTreeWithFlags : ActivityResultContract<Uri?, TreeUriSelection?>() {
    override fun createIntent(context: android.content.Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            input?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): TreeUriSelection? {
        if (resultCode != Activity.RESULT_OK) return null
        val uri = intent?.data ?: return null
        return TreeUriSelection(uri = uri, grantFlags = intent.flags)
    }
}
