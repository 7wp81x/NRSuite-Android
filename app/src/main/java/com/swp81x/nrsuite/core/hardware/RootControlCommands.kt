package com.swp81x.nrsuite.core.hardware

enum class RootAction(val label: String, val effect: String? = null) {
    ROOT_STATUS("Check root"), RADIO_STATUS("Radio status"), WIFI_CAPABILITIES("Wi-Fi driver capabilities"),
    WIFI_ON("Wi-Fi on", "Enable the phone's Wi-Fi radio."), WIFI_OFF("Wi-Fi off", "Disable Wi-Fi and disconnect its current connection."),
    BLUETOOTH_ON("Bluetooth on", "Enable Bluetooth."), BLUETOOTH_OFF("Bluetooth off", "Disable Bluetooth and disconnect connected accessories."),
    NFC_ON("NFC on", "Enable NFC."), NFC_OFF("NFC off", "Disable NFC."),
    KALI_HEALTH("Kali health"), KALI_INTERFACES("Kali interfaces"), KALI_TOOLS("Kali tool inventory"),
    KALI_NMAP_VERSION("Run nmap version"), KALI_TCPDUMP_VERSION("Run tcpdump version"),
    CHROOT_START("Start chroot", "Run the installed NetHunter initializer if required. It can mount shared device paths and change DNS, hostname and forwarding settings."),
    CHROOT_STOP("Stop idle chroot", "Unmount an idle Kali environment. Refuses while Kali processes are running. No processes are killed or files deleted."),
    SSH_STATUS("SSH status"), SSH_START("Start SSH", "Start the Kali SSH server using its existing configuration. It may accept network connections."), SSH_STOP("Stop SSH", "Stop Kali SSH. Existing SSH sessions may disconnect."),
    APACHE_STATUS("Web server status"), APACHE_START("Start web server", "Start Kali Apache using its existing network configuration."), APACHE_STOP("Stop web server", "Stop Kali Apache."),
    POSTGRES_STATUS("Database status"), POSTGRES_START("Start database", "Start Kali PostgreSQL using its existing configuration."), POSTGRES_STOP("Stop database", "Stop Kali PostgreSQL and disconnect database clients.")
}

/** Only fixed local operations are accepted. No free-form root command input. */
object RootControlCommands {
    private const val resolve = """
ROOT="${'$'}(readlink -f /data/local/nhsystem/kalifs 2>/dev/null)"
if [ ! -d "${'$'}ROOT" ]; then ROOT="${'$'}(readlink -f /data/local/nhsystem/kali-arm64)"; fi
case "${'$'}ROOT" in /data/local/nhsystem/*) ;; *) echo 'Unsupported Kali root path'; exit 20;; esac
[ -x "${'$'}ROOT/bin/sh" ] || { echo 'Kali root filesystem unavailable'; exit 21; }
"""
    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
    private fun kali(body: String) = resolve + "\nchroot \"\$ROOT\" /usr/bin/env -i HOME=/root USER=root LANG=C PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/sh -c " + quote(body)

    fun command(action: RootAction): String {
        val body = when (action) {
            RootAction.ROOT_STATUS -> "id; uname -r; getenforce"
            RootAction.RADIO_STATUS -> "echo Wi-Fi:; settings get global wifi_on; echo Bluetooth:; settings get global bluetooth_on; echo NFC:; dumpsys nfc | grep mState="
            RootAction.WIFI_CAPABILITIES -> "iw list"
            RootAction.WIFI_ON -> "cmd wifi set-wifi-enabled enabled"
            RootAction.WIFI_OFF -> "cmd wifi set-wifi-enabled disabled"
            RootAction.BLUETOOTH_ON -> "cmd bluetooth_manager enable"
            RootAction.BLUETOOTH_OFF -> "cmd bluetooth_manager disable"
            RootAction.NFC_ON -> "svc nfc enable"
            RootAction.NFC_OFF -> "svc nfc disable"
            RootAction.KALI_HEALTH -> kali("id; uname -r; cat /etc/os-release; test -r /proc/mounts && echo 'proc available'")
            RootAction.KALI_NMAP_VERSION -> kali("nmap --version")
            RootAction.KALI_TCPDUMP_VERSION -> kali("tcpdump --version")
            RootAction.KALI_INTERFACES -> kali("ip -brief link")
            RootAction.KALI_TOOLS -> kali("for tool in ip iw rfkill bluetoothctl hciconfig tcpdump nmap service; do command -v \$tool || true; done")
            RootAction.CHROOT_START -> resolve + """
if grep -q " ${'$'}ROOT/proc " /proc/mounts && grep -q " ${'$'}ROOT/dev " /proc/mounts; then
    echo 'Chroot already mounted; initializer skipped'
else
    [ -f /data/data/com.offsec.nethunter/scripts/bootkali_init ] || { echo 'Installed NetHunter initializer unavailable'; exit 22; }
    /system/bin/sh /data/data/com.offsec.nethunter/scripts/bootkali_init || exit ${'$'}?
fi
chroot "${'$'}ROOT" /usr/bin/id
"""
            RootAction.CHROOT_STOP -> resolve + """
for proc in /proc/[0-9]*/root; do
    target="${'$'}(readlink "${'$'}proc" 2>/dev/null)"
    case "${'$'}target" in "${'$'}ROOT"|"${'$'}ROOT/"*) echo 'Chroot busy: stop Kali services and terminals first. Nothing unmounted.'; exit 23;; esac
done
for part in dev/binderfs dev/pts dev/shm dev proc sys system sdcard; do
    if grep -q " ${'$'}ROOT/${'$'}part " /proc/mounts; then
        umount "${'$'}ROOT/${'$'}part" || { echo 'Unmount refused; inspect remaining mounts in NetHunter'; exit 24; }
    fi
done
if grep -q " ${'$'}ROOT/" /proc/mounts; then echo 'Additional mounts remain; use NetHunter manager'; exit 25; fi
echo 'Chroot is unmounted. Files preserved.'
"""
            else -> {
                val service = when {
                    action.name.startsWith("SSH_") -> "ssh"
                    action.name.startsWith("APACHE_") -> "apache2"
                    action.name.startsWith("POSTGRES_") -> "postgresql"
                    else -> error("Unsupported action")
                }
                val operation = action.name.substringAfterLast('_').lowercase()
                kali("service $service $operation")
            }
        }
        return "[ \"\$(id -u)\" = 0 ] || { echo 'Root access denied'; exit 13; }\necho NRSUITE_ROOT_OK\n" + body
    }
}
