# Third-Party Notices

NRSuite itself is licensed under the GNU General Public License v3.0 or later.
Third-party dependencies remain under their own licenses.

Notable bundled/runtime components include:

- AndroidX and Jetpack Compose — Apache License 2.0
- Kotlin standard library and coroutines — Apache License 2.0
- libusb (where bundled for native USB support) — GNU LGPL 2.1
- Other Gradle dependencies retain the licenses declared by their respective
  upstream projects and published Maven metadata.

When distributing an APK, retain this notice and all upstream license/notice
files required by the corresponding dependencies.

## Geolocation research references

The independent BSSID geolocation client was informed by the publicly visible
behavior and wire format documented by:

- WaveDigger — GNU AGPL-3.0 — https://github.com/7wp81x/wavedigger
- apple-corelocation-experiments — https://github.com/acheong08/apple-corelocation-experiments

No WaveDigger or apple-corelocation-experiments source code is bundled here.
The NRSuite implementation is a separate minimal Kotlin implementation.

## Map rendering

- Leaflet 1.9.4 — BSD 2-Clause — https://leafletjs.com/
- OpenStreetMap tile imagery — © OpenStreetMap contributors, ODbL —
  https://www.openstreetmap.org/copyright

Leaflet is bundled under `app/src/main/assets/leaflet/`. Map tile usage must
respect the selected tile provider's usage policy; the current prototype uses
the public OpenStreetMap tile service.
