package com.swp81x.nrsuite.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface

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
    var loadError by remember { mutableStateOf<String?>(null) }
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
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            overScrollMode = WebView.OVER_SCROLL_NEVER
            setBackgroundColor(0xFF0D0D0F.toInt())
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    loadError = description ?: "Unable to load the embedded map."
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NrSurface),
    ) {
        AndroidView(
            factory = { webView },
            update = { view ->
                if (view.tag != mapUri) {
                    view.tag = mapUri
                    loadError = null
                    view.loadUrl(mapUri)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        loadError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NrSurface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Map unavailable\n$error",
                    color = NrOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
