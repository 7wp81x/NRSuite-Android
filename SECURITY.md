# Security Policy

NRSuite is itself a security tool, so vulnerabilities in NRSuite's own code (as opposed to vulnerabilities it's designed to *find* in target systems) are taken seriously — particularly anything affecting the mesh encryption/authentication scheme, credential storage, or firmware flashing process.

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Instead:
1. Use GitHub's private vulnerability reporting (Security tab → "Report a vulnerability") if enabled on the repo, **or**
2. Email the maintainer directly at: `7wp81x.dev@gmail.com`

Please include:
- A description of the vulnerability and its potential impact
- Steps to reproduce (or a PoC, if applicable)
- Affected component (Android app / firmware / protocol / mesh)
- Your suggested severity, if you have one

## What Counts as In-Scope

- Flaws in the ESP-NOW mesh group-key handling, HMAC verification, or replay protection
- Flaws in `CredentialStore` or other local data-at-rest handling
- Flaws in the USB/serial flashing protocol that could allow malicious firmware injection
- Authentication/authorization bypass in the master/client activation handshake
- Memory-safety issues in firmware parsing code (frame codec, EAPOL/WPA handshake parsers, pcap parsers) that could be triggered by a malicious over-the-air frame

## Out of Scope

- Vulnerabilities in third-party target systems that NRSuite is designed to test (e.g., "WPA2 is crackable" is expected behavior, not a bug in NRSuite)
- Social engineering, physical attacks against maintainers
- Issues requiring physical possession of an already-provisioned, unlocked device

## Response Timeline

We'll aim to:
- Acknowledge your report within **5 business days**
- Provide an initial assessment within **14 days**
- Coordinate a disclosure timeline with you once a fix is ready — we ask for reasonable time to patch and release before public disclosure (standard 90-day guideline, negotiable based on severity and complexity)

## Credit

With your permission, we're happy to credit reporters in release notes once a fix ships.
