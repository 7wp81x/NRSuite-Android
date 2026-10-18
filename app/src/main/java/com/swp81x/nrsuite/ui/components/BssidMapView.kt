package com.swp81x.nrsuite.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BssidMapView(
    bssid: String,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double?,
    source: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapUri = remember(bssid, latitude, longitude, accuracyMeters, source) {
        Uri.parse("file:///android_asset/map/bssid_map.html")
            .buildUpon()
            .appendQueryParameter("lat", latitude.toString())
            .appendQueryParameter("lng", longitude.toString())
            .appendQueryParameter("accuracy", accuracyMeters?.toString() ?: "")
            .appendQueryParameter("bssid", bssid)
            .appendQueryParameter("source", source)
            .build()
            .toString()
    }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.loadsImagesAutomatically = true
            overScrollMode = WebView.OVER_SCROLL_NEVER
            setBackgroundColor(0xFF0D0D0F.toInt())
            webViewClient = WebViewClient()
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            if (view.tag != mapUri) {
                view.tag = mapUri
                view.loadUrl(mapUri)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}
