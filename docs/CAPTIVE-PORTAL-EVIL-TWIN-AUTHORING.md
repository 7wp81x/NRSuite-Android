# Captive Portal / Evil Twin HTML Authoring Guide

> **Authorized testing only.** Use these features only on networks, devices,
> and environments you own or have explicit written permission to test.
> Do not collect real credentials or personal data.

This guide covers the HTML page used by **Captive Portal** and **Evil Twin** in
the NRSuite Android app.

---

## 1. What the firmware serves

The ESP32 captive portal runs an HTTP server at:

```text
http://192.168.4.1/
```

The relevant routes are:

| Route | Method | Purpose |
|---|---|---|
| `/` | GET | Built-in NRSuite notice page |
| `/continue` | GET | Uploaded custom HTML page |
| `/login` | POST/GET | Receives form fields and emits `captive_data` |
| anything else | any | Serves the landing/custom page directly |

Important consequences:

- The initial page shown to a connecting client is the built-in notice page.
  The custom HTML is served at `/continue` after the user continues.
- Unmatched paths are answered with the landing page directly rather than a
  redirect. This makes OS captive-portal probes and arbitrary 404 requests
  resolve to the portal even when a client does not follow 3xx responses.
- The AP advertises itself as the DHCP DNS server and uses wildcard DNS.
  Plaintext IPv4 UDP DNS queries sent to a manually configured external
  resolver are best-effort intercepted and answered as `192.168.4.1`. This is
  enabled by default and can be disabled with the **Intercept custom DNS**
  switch for each app run. The DNS-only mode avoids allocating the normal PCAP
  queues, and each `portal_dns_hijack` event reports current free/minimum heap
  and largest block for memory monitoring. DNS over HTTPS/TLS, DNS over TCP,
  IPv6 DNS, and hardcoded IP-based probes are not guaranteed to be intercepted.
- Serve all CSS, JavaScript, and images **inline** in the HTML file.
  There is no static asset route for separate files.
- External CDN links may not load before the client completes captive-portal
  authentication. Prefer offline/inline styling.
- The portal is HTTP only. Never use it to collect real passwords or sensitive
  personal information.

---

## 2. Upload constraints

- Maximum uploaded HTML size: **24 KiB**.
- Upload as UTF-8.
- The Android app uploads the file in chunks and verifies the final size.
- Files above **16 KiB** show a size warning because the firmware allocates the
  full page in RAM and may reject an upload when free heap is low.
- Before starting a portal, the app queries `STATUS.heap`. It requires the page
  size plus the firmware's 40 KB safety margin and an additional app-side heap
  margin. If the preflight fails, the portal is not started.
- `RESET_HTML` or chunk-upload rejection is surfaced in the app; the portal is
  stopped rather than silently serving a placeholder.
- Do not embed large base64 images, fonts, or libraries; they count toward the
  24 KiB limit.
- Keep the page simple and mobile-first.

A minimal `viewport` declaration is strongly recommended:

```html
<meta name="viewport" content="width=device-width, initial-scale=1">
```

---

## 3. Form submission

Create a form that posts to `/login`:

```html
<form method="POST" action="/login">
  <label for="password">Wi-Fi password</label>
  <input id="password" name="password" type="password" required>
  <button type="submit">Connect</button>
</form>
```

Behavior:

- Every submitted POST field is forwarded to Android as `captive_data.data`.
- The field’s HTML `name` attribute becomes the captured key.
- Evil Twin checks `password` and then `pass` as password candidates.
- Captive Portal records all submitted fields.
- The firmware responds with a short HTML/JavaScript redirect page after a
  successful POST. Do not rely on the custom page staying visible after submit.

Example redirect behavior:

```text
POST /login -> short response -> browser returns to /?attempt=N
```

---

## 4. Android workflow

1. Configure the NRSuite root directory in **Settings**.
2. Open **Captive Portal** or **Evil Twin**.
3. In the **HTML Templates** tab, import the authored `.html` file.
   - Templates can be renamed and deleted later.
   - Duplicate content is rejected by SHA-256 hash.
   - The 24 KiB firmware limit is enforced at import time.
4. Select the saved template from the Overview dropdown, or use **Choose HTML**
   for a one-off file. HTML is required for both portal modes.
5. Select the SSID and channel.
   - Evil Twin prompts you to select a target AP from a scan.
   - The selected channel must be allowed by the active regulatory country.
6. Start the portal and wait for the HTML upload to report complete.
7. Connect an authorized test client to the NRSuite AP.
8. Open the built-in notice page and choose **Continue** to reach the custom HTML.
9. Submit test form values and verify them in the app’s portal/credential results.

The app performs a firmware heap preflight before start. If the portal rejects
`RESET_HTML`, a chunk, or the final upload, the portal is stopped and the error
is shown.

The app reports AP-start failures directly. If the channel is blocked, select a
different allowed channel or change the Wi-Fi regulatory country in **Settings**.

### Evil Twin specifics

- A custom HTML file is required before Evil Twin can start.
- Choosing a scanned target normally fills the target BSSID and channel.
- Evil Twin can optionally use a deauth/reconnect assist against the target and
  captures EAPOL material while the portal is running.
- Evil Twin and Captive Portal cannot run at the same time; both use the same
  firmware SoftAP and HTTP server.
- Evil Twin does not silently switch to another channel. If AP startup fails,
  fix the selected channel/country and retry.

### Captive Portal specifics

- A template or custom HTML file is required before Captive Portal can start;
  the firmware placeholder is not used as a silent fallback.
- Credential sessions are app-private but currently stored as plain JSON on the
  Android device. Treat captured test data as sensitive.

---

## 5. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Custom page never appears | The built-in notice is served at `/`; custom HTML is at `/continue` |
| Upload fails or completes partially | File exceeds 24 KiB, was not UTF-8, the device heap preflight failed, or upload was interrupted |
| Duplicate template rejected | Identical HTML content is already saved under another template name |
| Start blocked because HTML is missing | Select a saved template or choose a custom `.html` file |
| Device rejected RESET_HTML / HTML chunk | Free heap is too low for the full page; retry after a reboot or use a smaller page |
| External CSS/JS/images missing | Use inline assets only; there is no static file route |
| Form submission shows a blank/short page | Expected: `/login` returns a redirect script, not the custom page |
| No captured fields | Ensure inputs have `name` attributes and the form posts to `/login` |
| Evil Twin shows no password candidate | Use `name="password"` or `name="pass"` |
| AP fails to start | Channel may be disallowed by the country mask; change it in Settings |
| Captive page does not appear on a custom-DNS client | Plaintext IPv4 DNS is intercepted best-effort, but DoH/DoT, DNS over TCP, IPv6 DNS, and hardcoded IP probes cannot be forced. Open `http://192.168.4.1/` manually and verify the client is using an authorized test device |
| Redirect loop | Keep links relative and avoid external captive-portal detection URLs |

## 6. Privacy and safety

- Use only synthetic test values.
- Do not collect real credentials, tokens, or personal data.
- Inform participants that this is an authorized security-awareness exercise.
- Clear captured sessions and HTML files after testing.
- Do not expose the portal beyond the authorized test environment.

---

## 7. Recommended page structure

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Network Login</title>
  <style>
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
      background: #0d0d0f;
      color: #e8eaed;
      font-family: system-ui, sans-serif;
    }
    main {
      width: min(420px, 100%);
      padding: 22px;
      background: #181a1f;
      border: 1px solid #2a2e35;
      border-radius: 16px;
    }
    h1 { margin: 0 0 8px; font-size: 20px; }
    p { color: #9aa0a6; font-size: 14px; line-height: 1.5; }
    label { display: block; margin-top: 14px; font-size: 13px; }
    input {
      width: 100%;
      margin-top: 6px;
      padding: 13px;
      border: 1px solid #2a2e35;
      border-radius: 10px;
      background: #101216;
      color: #e8eaed;
    }
    button {
      width: 100%;
      margin-top: 18px;
      padding: 13px;
      border: 0;
      border-radius: 10px;
      background: #a3e635;
      color: #0d0d0f;
      font-weight: 700;
    }
    .warning {
      margin-top: 16px;
      padding: 10px;
      border: 1px solid rgba(239, 68, 68, .35);
      border-radius: 10px;
      color: #fca5a5;
      background: rgba(239, 68, 68, .08);
      font-size: 12px;
    }
  </style>
</head>
<body>
  <main>
    <h1>Authorized test portal</h1>
    <p>This page is part of an authorized security-awareness test.</p>
    <form method="POST" action="/login">
      <label for="password">Test password</label>
      <input id="password" name="password" type="password"
             autocomplete="off" required>
      <button type="submit">Continue</button>
    </form>
    <div class="warning">
      Do not enter a real password. Test data may be retained by NRSuite.
    </div>
  </main>
</body>
</html>

A ready-to-use example is available at
[`docs/examples/captive-portal-example.html`](examples/captive-portal-example.html).
