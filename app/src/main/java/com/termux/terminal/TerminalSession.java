package com.termux.terminal;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSH-backed terminal session.
 *
 * This is a trimmed, external-I/O variant of Termux's TerminalSession. It keeps
 * the public surface expected by TerminalView but does not launch a local PTY:
 * bytes flow through {@link ExternalTerminalTransport}, which NRSuite backs with
 * its SSH channel.
 */
public final class TerminalSession extends TerminalOutput {

    private static final int MAX_OUTPUT_QUEUE_CHUNKS = 128;
    private static final int MAX_OUTPUT_BATCH_BYTES = 32 * 1024;
    private static final long OUTPUT_DRAIN_TIME_BUDGET_MS = 6L;
    private static final int MAX_PENDING_OUTPUT_BYTES = 4 * 1024 * 1024;

    public final String mHandle = UUID.randomUUID().toString();

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final byte[] mUtf8InputBuffer = new byte[5];
    private final ArrayBlockingQueue<byte[]> mOutputQueue =
        new ArrayBlockingQueue<>(MAX_OUTPUT_QUEUE_CHUNKS);
    private final AtomicBoolean mOutputDrainScheduled = new AtomicBoolean(false);

    TerminalEmulator mEmulator;
    TerminalSessionClient mClient;
    private ExternalTerminalTransport mTransport;
    private final ByteArrayOutputStream mPendingOutput = new ByteArrayOutputStream();
    private volatile boolean mRunning = true;
    private volatile int mExitStatus = 0;

    public TerminalSession(@NonNull TerminalSessionClient client) {
        mClient = client;
    }

    public interface ExternalTerminalTransport {
        void write(byte[] data, int offset, int count);

        void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);

        void disconnect();
    }

    public void setExternalTransport(@Nullable ExternalTerminalTransport transport) {
        mTransport = transport;
    }

    public void updateTerminalSessionClient(@NonNull TerminalSessionClient client) {
        mClient = client;
        if (mEmulator != null) mEmulator.updateTerminalSessionClient(client);
    }

    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            mEmulator = new TerminalEmulator(
                this,
                columns,
                rows,
                cellWidthPixels,
                cellHeightPixels,
                null,
                mClient
            );
            boolean appendedPending = false;
            synchronized (mPendingOutput) {
                if (mPendingOutput.size() > 0) {
                    byte[] pending = mPendingOutput.toByteArray();
                    mPendingOutput.reset();
                    mEmulator.append(pending, pending.length);
                    appendedPending = true;
                }
            }
            if (appendedPending) notifyScreenUpdate();
        } else {
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
        if (mTransport != null) {
            mTransport.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    public void feedOutput(@NonNull byte[] data, int offset, int count) {
        if (count <= 0 || !mRunning) return;
        byte[] copy = new byte[count];
        System.arraycopy(data, offset, copy, 0, count);

        // Apply backpressure when terminal rendering cannot keep up with the
        // SSH stream. This prevents commands such as `yes` from queuing an
        // unbounded number of byte arrays on the main thread.
        boolean queued = false;
        while (mRunning) {
            try {
                if (mOutputQueue.offer(copy, 100, TimeUnit.MILLISECONDS)) {
                    queued = true;
                    break;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (queued) scheduleOutputDrain();
    }

    private void scheduleOutputDrain() {
        if (mOutputDrainScheduled.compareAndSet(false, true)) {
            mMainThreadHandler.post(this::drainOutputQueue);
        }
    }

    private void drainOutputQueue() {
        int processedBytes = 0;
        try {
            long deadline = SystemClock.uptimeMillis() + OUTPUT_DRAIN_TIME_BUDGET_MS;
            while (processedBytes < MAX_OUTPUT_BATCH_BYTES) {
                byte[] chunk = mOutputQueue.poll();
                if (chunk == null) break;
                appendChunk(chunk);
                processedBytes += chunk.length;
                if (processedBytes > 0 && SystemClock.uptimeMillis() >= deadline) break;
            }
        } finally {
            if (processedBytes > 0) notifyScreenUpdate();
            mOutputDrainScheduled.set(false);
            if (!mOutputQueue.isEmpty()) scheduleOutputDrain();
        }
    }

    private void appendChunk(@NonNull byte[] chunk) {
        if (mEmulator != null) {
            mEmulator.append(chunk, chunk.length);
            return;
        }
        synchronized (mPendingOutput) {
            if (mPendingOutput.size() + chunk.length > MAX_PENDING_OUTPUT_BYTES) {
                mPendingOutput.reset();
                byte[] notice = "\r\n[Output dropped while terminal view was starting]\r\n"
                    .getBytes(StandardCharsets.UTF_8);
                mPendingOutput.write(notice, 0, notice.length);
            }
            mPendingOutput.write(chunk, 0, chunk.length);
        }
    }

    public void markDisconnected(@Nullable String message) {
        mMainThreadHandler.post(() -> {
            if (mRunning) {
                mRunning = false;
                mExitStatus = -1;
                if (mEmulator != null && message != null && !message.isEmpty()) {
                    byte[] bytes = ("\r\n[" + message + "]\r\n").getBytes(StandardCharsets.UTF_8);
                    mEmulator.append(bytes, bytes.length);
                }
                notifyScreenUpdate();
                mClient.onSessionFinished(TerminalSession.this);
            }
        });
    }

    public String getTitle() {
        return mEmulator == null ? null : mEmulator.getTitle();
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    public synchronized boolean isRunning() {
        return mRunning;
    }

    public synchronized int getExitStatus() {
        return mExitStatus;
    }

    public int getPid() {
        return -1;
    }

    public void reset() {
        if (mEmulator != null) {
            mEmulator.reset();
            notifyScreenUpdate();
        }
    }

    public void finishIfRunning() {
        if (mRunning) {
            mRunning = false;
            mExitStatus = -1;
            if (mTransport != null) mTransport.disconnect();
        }
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        if (mRunning && mTransport != null) {
            mTransport.write(data, offset, count);
        }
    }

    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }
        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;
        if (codePoint <= 0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= 0b11111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= 0b1111111111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mClient.onTitleChanged(this);
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mClient.onColorsChanged(this);
    }

    protected void notifyScreenUpdate() {
        mClient.onTextChanged(this);
    }
}
