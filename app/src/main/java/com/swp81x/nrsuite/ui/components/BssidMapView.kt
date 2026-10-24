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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.swp81x.nrsuite.core.geo.BssidNearbyAccessPoint
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    nearbyAccessPoints: List<BssidNearbyAccessPoint> = emptyList(),
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val context = LocalContext.current
    var mapHtml by remember { mutableStateOf<String?>(null) }
    var assetError by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bssid, latitude, longitude, accuracyMeters, source, nearbyAccessPoints) {
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
                    // The map uses circle markers, so avoid broken image requests.
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
                    nearbyAccessPoints = nearbyAccessPoints,
                )
            }.onFailure { error ->
                assetError = error.message ?: "Could not read bundled map assets."
            }.getOrNull()
        }
    }

    val nearbyKey = remember(nearbyAccessPoints) {
        nearbyAccessPoints.joinToString(";") {
            "${it.bssid},${it.latitude},${it.longitude}," +
                "${it.accuracyMeters},${it.source},${it.distanceMeters}"
        }
    }
    val mapKey = remember(bssid, latitude, longitude, accuracyMeters, source, nearbyKey) {
        "$bssid|$latitude|$longitude|$accuracyMeters|$source|$nearbyKey"
    }
    val renderKey = remember(mapKey, mapHtml) {
        "$mapKey|${mapHtml?.hashCode()}"
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
                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?,
                ) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("BssidMapView", "map page started url=$url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("BssidMapView", "map page finished url=$url")
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    val description = error?.description?.toString() ?: "Unknown WebView error."
                    if (request?.isForMainFrame == true) {
                        loadError = description
                        Log.e("BssidMapView", "main-frame error: $description")
                    } else {
                        Log.w("BssidMapView", "resource error ${request?.url}: $description")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?,
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val description = "HTTP ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}"
                    if (request?.isForMainFrame == true) {
                        loadError = description
                    }
                    Log.e("BssidMapView", "HTTP error ${request?.url}: $description")
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
            .clip(shape)
            .background(NrSurface),
    ) {
        AndroidView(
            factory = { webView },
            update = { view ->
                val html = mapHtml
                if (html != null && view.tag != renderKey) {
                    view.tag = renderKey
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
        } else if (mapHtml == null) {
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
    nearbyAccessPoints: List<BssidNearbyAccessPoint>,
): String {
    val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val hasAccuracy = accuracy > 0.0
    val nearbyApsJson = JSONArray().apply {
        nearbyAccessPoints.forEach { ap ->
            put(
                JSONObject().apply {
                    put("bssid", ap.bssid)
                    put("latitude", ap.latitude)
                    put("longitude", ap.longitude)
                    put("accuracyMeters", ap.accuracyMeters ?: JSONObject.NULL)
                    put("source", ap.source)
                    put("distanceMeters", ap.distanceMeters)
                },
            )
        }
    }.toString()
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <title>BSSID Location</title>
          <style>$leafletCss</style>
          <style>
            html, body {
              width: 100%; height: 100%; margin: 0; padding: 0;
              overflow: hidden; background: #0d0d0f;
            }
            #map {
              position: absolute; top: 0; left: 0;
              width: 100vw; height: 100vh;
              background: #0d0d0f;
            }
            #status {
              position: absolute; left: 8px; right: 8px; bottom: 8px; z-index: 1000;
              display: block; padding: 8px 10px; border: 1px solid rgba(245,158,11,.45);
              border-radius: 8px; background: rgba(13,13,15,.92); color: #fbbf24;
              font: 12px system-ui, sans-serif;
            }
            .leaflet-container { background: #0d0d0f; }
            .bssid-map-marker {
              background: transparent;
              border: none;
              filter: drop-shadow(0 4px 5px rgba(0, 0, 0, 0.55));
            }
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
              var nearbyAps = $nearbyApsJson;
              console.log('BSSID_MAP: nearby markers=' + nearbyAps.length);
              var mapElement = document.getElementById('map');
              mapElement.style.position = 'absolute';
              mapElement.style.top = '0';
              mapElement.style.left = '0';
              mapElement.style.width = window.innerWidth + 'px';
              mapElement.style.height = window.innerHeight + 'px';
              var initialStyle = window.getComputedStyle(mapElement);
              var initialRect = mapElement.getBoundingClientRect();
              console.log('BSSID_MAP: pre-init viewport=' + window.innerWidth + 'x' + window.innerHeight +
                ' rect=' + initialRect.width + 'x' + initialRect.height +
                ' style=' + initialStyle.width + 'x' + initialStyle.height);
              var map = L.map('map', { zoomControl: true, attributionControl: true }).setView([lat, lng], 15);
              var tilesLoaded = 0;
              var tileErrors = 0;
              var status = document.getElementById('status');
              var mapRect = mapElement.getBoundingClientRect();
              console.log('BSSID_MAP: Leaflet=' + L.version +
                ' viewport=' + window.innerWidth + 'x' + window.innerHeight +
                ' mapRect=' + mapRect.width + 'x' + mapRect.height);
              var tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                referrerPolicy: 'origin',
                attribution: '&copy; OpenStreetMap contributors'
              });
              tiles.on('tileload', function () {
                tilesLoaded++;
                status.style.display = 'none';
                if (tilesLoaded === 1) {
                  var rect = mapElement.getBoundingClientRect();
                  console.log('BSSID_MAP: first tile loaded, mapRect=' +
                    rect.width + 'x' + rect.height);
                }
              });
              tiles.on('tileerror', function () {
                tileErrors++;
                console.log('BSSID_MAP: tile error count=' + tileErrors);
                if (tilesLoaded === 0) {
                  status.textContent = 'Map tiles unavailable. The BSSID result remains available in NRSuite.';
                  status.style.display = 'block';
                }
              });
              tiles.addTo(map);

              function makePopup(titleText, lines) {
                var popup = document.createElement('div');
                var popupTitle = document.createElement('strong');
                popupTitle.textContent = titleText;
                popup.appendChild(popupTitle);
                for (var i = 0; i < lines.length; i++) {
                  popup.appendChild(document.createElement('br'));
                  popup.appendChild(document.createTextNode(lines[i]));
                }
                return popup;
              }

              function makePinIcon(fillColor, width, height) {
                return L.divIcon({
                  className: 'bssid-map-marker',
                  html: '<svg width="' + width + '" height="' + height +
                    '" viewBox="0 0 34 44" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
                    '<path d="M17 42C17 42 32 26.2 32 15.8C32 7.1 25.3 1 17 1' +
                    'C8.7 1 2 7.1 2 15.8C2 26.2 17 42 17 42Z" ' +
                    'fill="' + fillColor + '" stroke="#0d0d0f" stroke-width="2"/>' +
                    '<circle cx="17" cy="15.8" r="6.2" fill="#0d0d0f"/>' +
                    '<circle cx="17" cy="15.8" r="2.2" fill="' + fillColor + '"/>' +
                    '</svg>',
                  iconSize: [width, height],
                  iconAnchor: [width / 2, height - 2],
                  popupAnchor: [0, -height + 5]
                });
              }

              function formatDistance(meters) {
                return meters < 1000
                  ? Math.round(meters) + ' m'
                  : (meters / 1000).toFixed(1) + ' km';
              }

              var selectedPopup = makePopup(bssid, [
                hasAccuracy ? 'Accuracy: ±' + Math.round(accuracy) + ' m' : 'Accuracy: unknown',
                'Source: ' + source
              ]);
              var selectedMarker = L.marker([lat, lng], {
                icon: makePinIcon('#a3e635', 34, 44),
                keyboard: false,
                riseOnHover: true
              }).addTo(map).bindPopup(selectedPopup);

              var mapPoints = [[lat, lng]];
              for (var apIndex = 0; apIndex < nearbyAps.length; apIndex++) {
                var ap = nearbyAps[apIndex];
                var nearbyPopup = makePopup(ap.bssid, [
                  'Nearby AP · ' + formatDistance(ap.distanceMeters) + ' away',
                  ap.accuracyMeters
                    ? 'Accuracy: ±' + Math.round(ap.accuracyMeters) + ' m'
                    : 'Accuracy: unknown',
                  'Source: ' + ap.source
                ]);
                L.marker([ap.latitude, ap.longitude], {
                  icon: makePinIcon('#38bdf8', 28, 36),
                  keyboard: false,
                  riseOnHover: true
                }).addTo(map).bindPopup(nearbyPopup);
                mapPoints.push([ap.latitude, ap.longitude]);
              }

              if (mapPoints.length > 1) {
                map.fitBounds(L.latLngBounds(mapPoints).pad(0.2), { maxZoom: 16 });
              }
              selectedMarker.openPopup();

              if (hasAccuracy) {
                L.circle([lat, lng], {
                  radius: accuracy, color: '#a3e635', weight: 1,
                  fillColor: '#a3e635', fillOpacity: 0.08
                }).addTo(map);
              }
              window.addEventListener('resize', function () {
                mapElement.style.width = window.innerWidth + 'px';
                mapElement.style.height = window.innerHeight + 'px';
                map.invalidateSize();
              });
              setTimeout(function () {
                map.invalidateSize();
                var rect = mapElement.getBoundingClientRect();
                console.log('BSSID_MAP: final mapRect=' + rect.width + 'x' + rect.height +
                  ' tiles=' + tilesLoaded + ' errors=' + tileErrors);
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
