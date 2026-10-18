package com.swp81x.nrsuite.ui.components

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Real HTTPS base origin for the generated document. The document itself is
// loaded with loadDataWithBaseURL, so this doesn't require hosting any files.
private const val MAP_BASE_URL = "https://github.com/7wp81x/NRSuite-Android/"

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
    var mapHtml by remember { mutableStateOf<String?>(null) }
    var assetError by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bssid, latitude, longitude, accuracyMeters, source) {
        mapHtml = null
        assetError = null
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            assetError = "Invalid coordinates."
            return@LaunchedEffect
        }
        mapHtml = withContext(Dispatchers.IO) {
            runCatching {
                val leafletCss = context.assets.open("leaflet/leaflet.css")
                    .bufferedReader()
                    .use { it.readText() }
                    // Avoid broken image requests; the map uses circle markers.
                    .replace(Regex("""url\(images/[^)]*\)"""), "none")
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
            }.onFailure { error ->
                assetError = error.message ?: "Could not read bundled map assets."
            }.getOrNull()
        }
    }

    val mapKey = remember(bssid, latitude, longitude, accuracyMeters, source) {
        "$bssid|$latitude|$longitude|$accuracyMeters|$source"
    }

    val webView = remember {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            setBackgroundColor(Color.rgb(13, 13, 15))
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        loadError = error?.description?.toString() ?: "Unable to load the embedded map."
                    }
                }

            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(
                        "BssidMapView",
                        "${consoleMessage.message()} " +
                            "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                    )
                    return true
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
        val html = mapHtml
        if (html != null) {
            AndroidView(
                factory = { webView },
                update = { view ->
                    if (view.tag != mapKey) {
                        view.tag = mapKey
                        loadError = null
                        view.loadDataWithBaseURL(
                            MAP_BASE_URL,
                            html,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        val error = assetError ?: loadError
        if (error != null) {
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
        } else if (html == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NrSurface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading map...",
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
    val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
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
              var tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                referrerPolicy: 'origin',
                attribution: '&copy; OpenStreetMap contributors'
              });
              tiles.on('tileload', function () {
                tilesLoaded++;
                status.style.display = 'none';
              });
              tiles.on('tileerror', function () {
                tileErrors++;
                if (tilesLoaded === 0) {
                  status.textContent = 'Map tiles unavailable. The BSSID result remains available in NRSuite.';
                  status.style.display = 'block';
                }
              });
              tiles.addTo(map);

              var box = document.createElement('div');
              var title = document.createElement('strong');
              title.textContent = bssid;
              box.appendChild(title);
              box.appendChild(document.createElement('br'));
              box.appendChild(document.createTextNode(
                hasAccuracy ? 'Accuracy: ±' + Math.round(accuracy) + ' m' : 'Accuracy: unknown'
              ));
              box.appendChild(document.createElement('br'));
              box.appendChild(document.createTextNode('Source: ' + source));

              L.circleMarker([lat, lng], {
                radius: 8, color: '#a3e635', weight: 2, fillColor: '#a3e635', fillOpacity: 0.45
              }).addTo(map).bindPopup(box).openPopup();

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
                }
              }, 1500);
            })();
          </script>
        </body>
        </html>
    """
}

private fun jsString(value: String): String = JSONObject.quote(value)
