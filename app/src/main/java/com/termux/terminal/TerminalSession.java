package com.termux.terminal;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * SSH-backed terminal session.
 *
 * This is a trimmed, external-I/O variant of Termux's TerminalSession. It keeps
 * the public surface expected by TerminalView but does not launch a local PTY:
 * bytes flow through {@link ExternalTerminalTransport}, which NRSuite backs with
 * its SSH channel.
 */
public final class TerminalSession extends TerminalOutput {

    public final String mHandle = UUID.randomUUID().toString();

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final byte[] mUtf8InputBuffer = new byte[5];

    TerminalEmulator mEmulator;
    TerminalSessionClient mClient;
    private ExternalTerminalTransport mTransport;
    private final ByteArrayOutputStream mPendingOutput = new ByteArrayOutputStream();
    private boolean mRunning = true;
    private int mExitStatus = 0;

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
            synchronized (mPendingOutput) {
                if (mPendingOutput.size() > 0) {
                    byte[] pending = mPendingOutput.toByteArray();
                    mPendingOutput.reset();
                    mEmulator.append(pending, pending.length);
                }
            }
        } else {
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
        if (mTransport != null) {
            mTransport.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    public void feedOutput(@NonNull byte[] data, int offset, int count) {
        if (count <= 0) return;
        byte[] copy = new byte[count];
        System.arraycopy(data, offset, copy, 0, count);
        mMainThreadHandler.post(() -> {
            if (mEmulator == null) {
                synchronized (mPendingOutput) {
                    mPendingOutput.write(copy, 0, copy.length);
                }
                return;
            }
            mEmulator.append(copy, copy.length);
            notifyScreenUpdate();
        });
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
