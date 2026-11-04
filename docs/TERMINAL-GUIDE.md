# NRSuite Terminal / Termux SSH Guide

This guide covers the native SSH terminal, Termux setup, local key authentication,
terminal settings, autoconnect/reconnect behavior, and terminal shortcuts.

> Authorized use only. Only connect to systems and accounts you own or are
> explicitly authorized to access.

---

## 1. Overview

The **Terminal** tab is a native terminal backed by an SSH connection to
OpenSSH running in the Termux Android app. It is not a web terminal or a remote
shell through the NRSuite firmware.

Default local connection:

```text
Host:      127.0.0.1
Port:      8022
Username:  nrsuite
Auth:      publickey only
Key:       Ed25519 recommended
```

NRSuite forces the username to `nrsuite` for `127.0.0.1` and `localhost`
connections. The SSH server and key remain in Termux; NRSuite stores the imported
private key encrypted using Android Keystore.

---

## 2. Termux setup

Install Termux and run the one-time setup in Termux. The app's setup card can copy
the current command block, but the required steps are:

```bash
pkg update -y && pkg upgrade -y
pkg install openssh termux-services -y
termux-setup-storage

mkdir -p ~/.ssh
ssh-keygen -t ed25519 -f ~/.ssh/nrsuite -N '' -C nrsuite-app

touch ~/.ssh/authorized_keys
sed -i '/nrsuite-app/d' ~/.ssh/authorized_keys
printf 'from="127.0.0.1,::1",no-port-forwarding,no-agent-forwarding,no-X11-forwarding %s\n' \
  "$(cat ~/.ssh/nrsuite.pub)" >> ~/.ssh/authorized_keys

chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys

mkdir -p ~/storage/shared/NRSuite
cp ~/.ssh/nrsuite ~/storage/shared/NRSuite/nrsuite_ssh_key
cp ~/.ssh/nrsuite.pub ~/storage/shared/NRSuite/nrsuite_ssh_key.pub

sv-enable sshd
sv up sshd
```

The setup command copied by NRSuite also uses this same workflow. The private and
public key files are exported to the shared NRSuite folder only for the initial
import.

To verify the server from Termux:

```bash
ssh -vvv nrsuite@127.0.0.1 -p 8022 -i ~/.ssh/nrsuite -o IdentitiesOnly=yes
```

Expected authorization line:

```text
Authenticated to 127.0.0.1 ([127.0.0.1]:8022) using "publickey".
```

---

## 3. Importing the SSH key

1. Open **Terminal**.
2. Confirm Termux shows as installed.
3. Use the key import action or let NRSuite check the NRSuite root automatically.
4. Select `~/storage/shared/NRSuite/nrsuite_ssh_key`.
5. If a public key file is requested, select `nrsuite_ssh_key.pub`.

NRSuite validates the private key with JSch before saving it. The key and
passphrase are encrypted with Android Keystore. Malformed keys or a wrong
passphrase are rejected instead of being stored.

### Key fingerprint

The Terminal setup screen shows a SHA-256 fingerprint for the imported public key.
Compare it with:

```bash
ssh-keygen -lf ~/.ssh/nrsuite.pub
```

---

## 4. Authentication behavior

NRSuite uses:

```text
PreferredAuthentications = publickey
```

Supported public-key algorithms include Ed25519 and RSA variants. Ed25519 uses
JSch's BouncyCastle signature implementation on Android, because Android's JCE
does not provide Ed25519 here.

If Ed25519 fails on a modified setup, an RSA 4096 key can be generated as an
interoperability fallback:

```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/nrsuite -N '' -C nrsuite-app
```

Then repeat the `authorized_keys` and import steps above.

### Host-key pinning

NRSuite pins the Termux SSH host key on first connection. If the key changes, the
connection is blocked and a dialog displays the pinned and new SHA-256
fingerprints. Only choose **Trust and reconnect** if the change was intentional,
such as after reinstalling Termux.

---

## 5. Terminal defaults

Fresh-install defaults:

| Setting | Default |
|---|---|
| Autoconnect | Enabled |
| Show Terminal Shortcuts card on Home | Enabled |
| Save password | Disabled |
| Default font size | 34 dp |
| Cursor style | Block |
| Cursor blink | 500 ms |
| Extra keys | Shown only while the keyboard is open |
| Authentication | Public key only |

Existing installations keep their previously saved settings.

### Autoconnect

When autoconnect is enabled:

- Entering the Terminal tab attempts one automatic connection if a saved key and
  connection configuration exist.
- A manual **Disconnect SSH** leaves the setup screen visible while the user stays
  on Terminal.
- Leaving Terminal and returning re-arms autoconnect for a new attempt.
- **Force reconnect** in the session menu closes the old transport and reconnects
  immediately.

### Sessions and tabs

- Tabs are independent SSH shell channels over one SSH transport.
- **Close current tab** is disabled when only one tab remains.
- **Disconnect SSH** is the explicit action that closes the SSH transport.
- Normal tab close asks the remote shell to exit before falling back to a forced
  channel close.

---

## 6. Keyboard and display

- Tapping the terminal shows the Android keyboard.
- The keyboard toggle in the terminal toolbar shows or hides the keyboard.
- Extra keys appear above the keyboard only while it is open.
- Pinch to zoom changes the terminal font size.
- The terminal keeps the Android status-bar area clear while the keyboard is open.
- Landscape and portrait layouts preserve the same IME spacing behavior.

If the keyboard overlays the terminal on a specific Android build, include the
device model and Android version in a bug report.

---

## 7. Terminal shortcuts

Open the shortcut quick sheet from the terminal toolbar. It provides:

- Search
- Category filters
- Tap-to-run shortcuts
- `+` to create a shortcut
- Manage/settings actions

Shortcuts can use the following run modes:

- **Current session**
- **New session**
- **New temporary session**
- **Insert only**
- **Background task**

A shortcut can be pinned to the Home screen and can optionally ask for
confirmation before running.

### Parameterized shortcuts

Use `{{name}}` in a command to declare a parameter:

```bash
nmap -sV {{target}}
ping -c {{count}} {{host}}
ssh {{user}}@{{host}}
```

When the shortcut is run:

1. A form is shown for every detected parameter.
2. Repeated placeholders with the same name are filled once.
3. A command preview is shown.
4. **Run** is enabled only when all parameters are filled.
5. The parameter form acts as the confirmation step.

Parameter names should use letters, numbers, `_`, `-`, or `.`.

### Built-in examples

A fresh shortcut store seeds editable examples:

**Basics**

- `ls -lah`
- `df -h`
- `ip addr`

**Network**

- `ping -c 4 {{host}}`
- `nmap -sV {{target}}`
- `curl -I {{url}}`

**Files**

- `find . -type f -iname '{{pattern}}'`
- `tail -n {{lines}} {{file}}`

They are normal shortcuts: edit or delete them freely. Built-ins are seeded only
once, so deleting one does not recreate it on the next launch.

---

## 8. Troubleshooting

### Termux SSH authentication fails

1. Confirm Termux `sshd` is running:
   ```bash
   sv up sshd
   ```
2. Test the same key from Termux:
   ```bash
   ssh -vvv nrsuite@127.0.0.1 -p 8022 -i ~/.ssh/nrsuite -o IdentitiesOnly=yes
   ```
3. Confirm the public key is present in `~/.ssh/authorized_keys`.
4. Re-import the private key if it was changed or regenerated.
5. Check `NRSuiteTerminalJSch` logs:
   ```bash
   adb logcat -s NRSuiteTerminalJSch:* NRSuiteTerminal:* AndroidRuntime:E
   ```

The expected log indicates that `ssh-ed25519` is available for the imported
identity.

### Pinned host key changed

Reinstalling Termux generates a new SSH host key. Review both fingerprints and
only trust the new key if the change is intentional. The explicit **Clear pinned
SSH host key** control is available in Terminal Settings.

### SSH is connected but input or Enter is not working

- Tap the terminal view once to restore input focus.
- Toggle the keyboard off and on.
- Switch tabs and return to the active tab.
- Include a logcat capture in the bug report if the problem persists.

### Heavy output makes the terminal sluggish

NRSuite bounds and batches terminal output so commands such as `yes` do not grow
an unbounded UI queue. If output becomes unresponsive, press Ctrl-C or use the
keyboard controls to stop the command.

---

## 9. Planned Termux tools

Future releases will build on the SSH terminal for:

- Nmap scanning with structured XML output and logical topology visualization.
- Packet capture workflows with PCAP export.
- Optional Wireshark integration through Termux:X11 with a minimal window manager.
- A Termux Tools hub for package checks, launch actions, and GUI/terminal tools.

These are planned features and are not part of the terminal implementation
documented above.
