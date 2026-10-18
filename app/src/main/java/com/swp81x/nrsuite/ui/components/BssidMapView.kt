package com.swp81x.nrsuite.ui.components

import android.annotation.SuppressLint
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

private const val MAP_BASE_URL = "https://appassets.androidplatform.net/"

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
    val mapHtml = remember(bssid, latitude, longitude, accuracyMeters, source) {
        val leafletCss = context.assets.open("leaflet/leaflet.css")
            .bufferedReader()
            .use { it.readText() }
        val leafletJs = context.assets.open("leaflet/leaflet.js")
            .bufferedReader()
            .use { it.readText() }
        buildMapHtml(
            leafletCss = leafletCss,
            leafletJs = leafletJs,
            bssid = bssid,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            source = source,
        )
    }
    val mapKey = remember(bssid, latitude, longitude, accuracyMeters, source) {
        "$bssid|$latitude|$longitude|$accuracyMeters|$source"
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
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
                if (view.tag != mapKey) {
                    view.tag = mapKey
                    loadError = null
                    view.loadDataWithBaseURL(
                        MAP_BASE_URL,
                        mapHtml,
                        "text/html",
                        "UTF-8",
                        null,
                    )
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

private fun buildMapHtml(
    leafletCss: String,
    leafletJs: String,
    bssid: String,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double?,
    source: String,
): String {
    val accuracy = accuracyMeters ?: 0.0
    val hasAccuracy = accuracy > 0.0
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <title>BSSID Location</title>
          <style>$leafletCss</style>
          <style>
            html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #0d0d0f; }
            #status {
              position: absolute; left: 8px; right: 8px; bottom: 8px; z-index: 1000;
              display: block; padding: 8px 10px; border: 1px solid rgba(245,158,11,.45);
              border-radius: 8px; background: rgba(13,13,15,.92); color: #fbbf24;
              font: 12px system-ui, sans-serif;
            }
            .leaflet-container { background: #0d0d0f; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <div id="status">Loading map...</div>
          <script>
            window.onerror = function (message, source, line) {
              var status = document.getElementById('status');
              status.textContent = 'Map script error: ' + message + ' (line ' + line + ')';
              status.style.display = 'block';
            };
          </script>
          <script>$leafletJs</script>
          <script>
            (function () {
              var lat = $latitude;
              var lng = $longitude;
              var accuracy = $accuracy;
              var hasAccuracy = $hasAccuracy;
              var bssid = ${jsString(bssid)};
              var source = ${jsString(source)};
              var map = L.map('map', { zoomControl: true, attributionControl: true }).setView([lat, lng], 15);
              var tilesLoaded = 0;
              var tileErrors = 0;
              var status = document.getElementById('status');
              var tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; OpenStreetMap contributors'
              });
              tiles.on('tileload', function () {
                tilesLoaded++;
                if (tileErrors === 0) status.style.display = 'none';
              });
              tiles.on('tileerror', function () {
                tileErrors++;
                if (tilesLoaded === 0) {
                  status.textContent = 'Map tiles unavailable. The BSSID result remains available in NRSuite.';
                  status.style.display = 'block';
                }
              });
              tiles.addTo(map);
              L.circleMarker([lat, lng], {
                radius: 8, color: '#a3e635', weight: 2, fillColor: '#a3e635', fillOpacity: 0.45
              }).addTo(map).bindPopup(
                '<strong>' + bssid + '</strong><br>' +
                (hasAccuracy ? 'Accuracy: ±' + Math.round(accuracy) + ' m<br>' : 'Accuracy: unknown<br>') +
                'Source: ' + source
              ).openPopup();
              if (hasAccuracy) {
                L.circle([lat, lng], {
                  radius: accuracy, color: '#a3e635', weight: 1,
                  fillColor: '#a3e635', fillOpacity: 0.08
                }).addTo(map);
              }
              setTimeout(function () {
                map.invalidateSize();
                if (tileErrors > 0 && tilesLoaded === 0) {
                  status.textContent = 'Map tiles unavailable. The BSSID result remains available in NRSuite.';
                  status.style.display = 'block';
                } else if (tilesLoaded > 0) {
                  status.style.display = 'none';
                }
              }, 1200);
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun jsString(value: String): String =
    "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
