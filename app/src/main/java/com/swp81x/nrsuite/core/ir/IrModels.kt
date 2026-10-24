package com.swp81x.nrsuite.core.ir

enum class IrSignalType {
    RAW,
    PARSED,
}

/**
 * A signal imported from a Flipper Zero-style .ir file.
 *
 * Parsed signals retain their protocol/address/command fields so they can be
 * encoded later. Raw signals already contain the mark/space timings in
 * microseconds.
 */
data class IrSignal(
    val id: String = "",
    val name: String,
    val sourceName: String = "",
    val type: IrSignalType,
    val frequencyHz: Int = 38000,
    val dutyCycle: Double? = null,
    val protocol: String? = null,
    val address: Long? = null,
    val command: Long? = null,
    val bits: Int? = null,
    val rawTimingsUs: List<Int> = emptyList(),
)

data class IrPhoneCapabilities(
    val hasEmitter: Boolean = false,
    val carrierRanges: List<IntRange> = emptyList(),
)

sealed interface IrEncodeResult {
    data class Success(
        val timingsUs: IntArray,
        val frequencyHz: Int,
    ) : IrEncodeResult

    data class Failure(val message: String) : IrEncodeResult
}

enum class IrRemoteSource {
    IMPORTED,
    DATABASE,
    RECEIVED,
}

enum class IrTransmitBackend {
    AUTO,
    PHONE,
    ESP32,
}

data class IrRemoteSummary(
    val id: String,
    val name: String,
    val fileName: String,
    val relativePath: String,
    val documentUri: String,
    val source: IrRemoteSource,
    val category: String,
    val brand: String = "",
    val favorite: Boolean = false,
    val lastUsedAtMs: Long = 0L,
    val buttonCount: Int = 0,
    val protocols: List<String> = emptyList(),
    val buttonNames: List<String> = emptyList(),
    val lastModifiedMs: Long = 0L,
    val sizeBytes: Long = 0L,
)

data class IrScanProgress(
    val scanned: Int,
    val total: Int,
    val currentFile: String? = null,
    val discoveredCount: Int = 0,
) {
    val discovering: Boolean
        get() = total <= 0

    val percent: Int
        get() = if (total <= 0) 0 else ((scanned * 100) / total).coerceIn(0, 100)
}

data class IrRemote(
    val summary: IrRemoteSummary,
    val signals: List<IrSignal>,
)


data class IrSearchCandidate(
    val remoteId: String,
    val remoteName: String,
    val category: String,
    val brand: String,
    val buttonName: String,
)

enum class IrSearchRunState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED,
    ERROR,
}

data class IrSearchUiState(
    val runState: IrSearchRunState = IrSearchRunState.IDLE,
    val candidates: List<IrSearchCandidate> = emptyList(),
    val currentIndex: Int = 0,
    val intervalMs: Int = 1200,
    val autoStepping: Boolean = false,
    val loopingCurrent: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val foundCandidates: List<IrSearchCandidate> = emptyList(),
) {
    val currentCandidate: IrSearchCandidate?
        get() = candidates.getOrNull(currentIndex)
}

data class IrCommonButton(
    val name: String,
    val count: Int,
)

data class IrCommonButtonsState(
    val category: String = "All categories",
    val brand: String = "All brands",
    val minRemotes: Int = 10,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val buttons: List<IrCommonButton> = emptyList(),
    val errorMessage: String? = null,
)

enum class IrDatabasePhase {
    IDLE,
    DOWNLOADING,
    EXTRACTING,
    INDEXING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class IrDatabaseUiState(
    val phase: IrDatabasePhase = IrDatabasePhase.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val extractedFiles: Int = 0,
    val totalFiles: Int = 0,
    val currentItem: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val retryAttempt: Int = 0,
    val maxRetryAttempts: Int = 0,
    val canRetry: Boolean = false,
) {
    val running: Boolean
        get() = phase == IrDatabasePhase.DOWNLOADING ||
            phase == IrDatabasePhase.EXTRACTING ||
            phase == IrDatabasePhase.INDEXING

    val downloadPercent: Int?
        get() = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            null
        }

    val extractionPercent: Int?
        get() = if (totalFiles > 0) {
            ((extractedFiles * 100) / totalFiles).coerceIn(0, 100)
        } else {
            null
        }
}

data class IrReceivedSignal(
    val id: String,
    val frequencyHz: Int = 38000,
    val protocol: String = "RAW",
    val gpio: Int? = null,
    val timingsUs: List<Int> = emptyList(),
    val capturedAtMs: Long = System.currentTimeMillis(),
    val overflow: Boolean = false,
)

data class IrLearnedButton(
    val id: String,
    val name: String,
    val frequencyHz: Int = 38000,
    val protocol: String = "RAW",
    val timingsUs: List<Int> = emptyList(),
    val capturedAtMs: Long = System.currentTimeMillis(),
    val overflow: Boolean = false,
)

enum class IrJammerPhase {
    IDLE,
    RUNNING,
    COMPLETED,
    ERROR,
}

data class IrJammerUiState(
    val phase: IrJammerPhase = IrJammerPhase.IDLE,
    val totalMs: Long = 0L,
    val remainingMs: Long = 0L,
    val unlimited: Boolean = false,
    val backendLabel: String = "",
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val running: Boolean
        get() = phase == IrJammerPhase.RUNNING

    val remainingSeconds: Int
        get() = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)
}

data class IrDatabaseMetadata(
    val sourceUrl: String = "",
    val archiveVersion: String = "",
    val downloadedAtMs: Long = 0L,
    val indexedAtMs: Long = 0L,
    val archiveBytes: Long = 0L,
    val extractedFiles: Int = 0,
    val indexedRemotes: Int = 0,
    val destinationPath: String = "",
)
