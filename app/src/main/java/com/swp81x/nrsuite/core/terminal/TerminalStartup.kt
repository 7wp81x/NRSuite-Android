package com.swp81x.nrsuite.core.terminal

import java.security.MessageDigest

/**
 * App-controlled terminal startup text. The generated script is compared with
 * ~/.NRSuite/nr.sh over SSH and uploaded when missing or stale, so the user does
 * not need to copy anything through shared storage.
 *
 * The script is sourced into the current interactive shell, which is required
 * for its PS1 change to affect that shell. It never edits .bashrc, .profile, or
 * any other persistent shell configuration.
 */
internal object TerminalStartup {
    const val REMOTE_DIRECTORY = ".NRSuite"
    const val REMOTE_SCRIPT_NAME = "nr.sh"
    const val REMOTE_SCRIPT_PATH = "$REMOTE_DIRECTORY/$REMOTE_SCRIPT_NAME"

    private const val ESC = "\u001B"
    private const val RESET = "$ESC[0m"
    private const val BOLD_BRIGHT_GREEN = "$ESC[1;92m"
    private const val BOLD_BRIGHT_BLACK = "$ESC[1;90m"

    /**
     * Compact Bash prompt. `\w` keeps the working directory, `PROMPT_DIRTRIM=2`
     * shortens deeply nested paths, and `\$` renders `$` for a normal user and
     * `#` for root. Zsh and generic POSIX-shell fallbacks are included.
     */
    val promptCommand: String =
        "if [ -n \"\$BASH_VERSION\" ]; then " +
            "export PROMPT_DIRTRIM=2; " +
            "export PS1='\\[\\e[1;92m\\]NRSuite\\[\\e[0m\\] " +
            "\\[\\e[1;90m\\]\\w\\[\\e[0m\\] " +
            "\\[\\e[1;92m\\]\\\$\\[\\e[0m\\] '; " +
            "elif [ -n \"\$ZSH_VERSION\" ]; then " +
            "export PROMPT='%F{green}NRSuite%f %F{242}%2~%f %F{green}%(!.#.$)%f '; " +
            "else " +
            "export PS1='NRSuite \\$ '; " +
            "fi"

    fun banner(versionName: String): String = buildString {
        append(RESET)
        append(" v")
        append(versionName)
        append("\r\n")
        append(BOLD_BRIGHT_GREEN)
        append("  _   _ ____ ")
        append(BOLD_BRIGHT_BLACK)
        append(" ____        _ _          \r\n")
        append(BOLD_BRIGHT_GREEN)
        append(" | \\ | |  _ \\")
        append(BOLD_BRIGHT_BLACK)
        append("/ ___| _   _(_) |_ ___  \r\n")
        append(BOLD_BRIGHT_GREEN)
        append(" |  \\| | |_) \\")
        append(BOLD_BRIGHT_BLACK)
        append("___ \\| | | | | __/ _ \\ \r\n")
        append(BOLD_BRIGHT_GREEN)
        append(" | |\\  |  _ < ")
        append(BOLD_BRIGHT_BLACK)
        append("___) | |_| | | ||  __/ \r\n")
        append(BOLD_BRIGHT_GREEN)
        append(" |_| \\_|_| \\_\\")
        append(BOLD_BRIGHT_BLACK)
        append("____/ \\__,_|_|\\__\\___| \r\n\r\n")
        append(" [ Developed by 7wp81x ]\r\n\r\n")
        append(RESET)
    }

    /**
     * Script uploaded to ~/.NRSuite/nr.sh over SSH and sourced on each new
     * shell. Arguments: show_banner (0/1), apply_prompt (0/1).
     *
     * The banner printf layout and green/gray split intentionally follow the
     * user-provided nr.sh styling. VERSION_CODE is generated from the app's
     * BuildConfig.VERSION_NAME, so it does not need manual updates.
     */
    fun script(versionName: String): String = SCRIPT_TEMPLATE
        .replace("@VERSION@", versionName)
        .replace("@D@", "$")
        .trimIndent() + "\n"

    /**
     * Source the remote script with the current options. The file must match the
     * expected SHA-256 before it is sourced. If it is missing, changed, or cannot
     * be verified, fall back to equivalent inline startup text instead of
     * executing an unverified file.
     */
    fun executeCommand(
        versionName: String,
        showBanner: Boolean,
        applyPrompt: Boolean,
        useRemoteScript: Boolean,
    ): String? {
        if (!showBanner && !applyPrompt) return null
        val fallback = startupCommand(versionName, showBanner, applyPrompt)
            ?: return null
        if (!useRemoteScript) return fallback
        val show = if (showBanner) "1" else "0"
        val prompt = if (applyPrompt) "1" else "0"
        val expectedHash = sha256Hex(script(versionName).toByteArray(Charsets.UTF_8))
        return "if [ -r \"\$HOME/$REMOTE_SCRIPT_PATH\" ] && " +
            "[ \"\$(sha256sum \"\$HOME/$REMOTE_SCRIPT_PATH\" 2>/dev/null | cut -d' ' -f1)\" = " +
            "\"$expectedHash\" ]; then " +
            ". \"\$HOME/$REMOTE_SCRIPT_PATH\" $show $prompt; " +
            "else $fallback; fi"
    }

    /**
     * Inline fallback used when the remote script is unavailable. Kept as a
     * single line so it can be sent reliably over the SSH PTY.
     */
    fun startupCommand(
        versionName: String,
        showBanner: Boolean,
        applyPrompt: Boolean,
    ): String? {
        if (!showBanner && !applyPrompt) return null
        val parts = mutableListOf(
            "stty -echo 2>/dev/null",
            "clear 2>/dev/null || printf '\\033[2J\\033[H'",
        )
        if (showBanner) {
            parts += "printf '%b' ${shellSingleQuote(shellPrintfEscaped(banner(versionName)))}"
        }
        if (applyPrompt) {
            parts += promptCommand
        }
        parts += "stty echo 2>/dev/null"
        return parts.joinToString("; ")
    }

    fun isLocalTermuxHost(host: String): Boolean {
        val normalized = host.trim()
        return normalized == "127.0.0.1" || normalized.equals("localhost", ignoreCase = true)
    }

    private fun sha256Hex(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { "%02x".format(it) }

    private fun shellPrintfEscaped(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace(ESC, "\\033")
            .replace("\r", "\\r")
            .replace("\n", "\\n")

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private val SCRIPT_TEMPLATE = """
        #!/usr/bin/env bash
        # NRSuite terminal startup helper. Generated by the NRSuite Android app.
        # Sourced into the current shell; no shell profile is modified.
        __nrsuite_show_banner="${'$'}{1:-1}"
        __nrsuite_apply_prompt="${'$'}{2:-1}"
        if [ "${'$'}__nrsuite_show_banner" != "1" ] && [ "${'$'}__nrsuite_apply_prompt" != "1" ]; then
            unset __nrsuite_show_banner __nrsuite_apply_prompt
            return 0 2>/dev/null || exit 0
        fi
        stty -echo 2>/dev/null
        clear 2>/dev/null || printf '\033[2J\033[H'
        if [ "${'$'}__nrsuite_show_banner" = "1" ]; then
            GRAY='\033[1;90m'    # Literal characters \033
            GRAY=@D@'\033[1;90m'  # Actual ANSI escape character

            VERSION_CODE="v@VERSION@"
            printf  "\033[0m ${'$'}{VERSION_CODE}\n"
            printf  "\033[1;92m  _   _ ____ %s ____        _ _          \n" ${'$'}GRAY
            printf  "\033[1;92m | \ | |  _ \%s/ ___| _   _(_) |_ ___  \n" ${'$'}GRAY
            printf  "\033[1;92m |  \| | |_) \%s___ \| | | | | __/ _ \ \n" ${'$'}GRAY
            printf  "\033[1;92m | |\  |  _ < %s___) | |_| | | ||  __/ \n" ${'$'}GRAY
            printf  "\033[1;92m |_| \_|_| \_\%s____/ \__,_|_|\__\___| \n\n" ${'$'}GRAY
            printf  "\033[1;90m [ Developed by 7wp81x ]\033[0m\n\n"
        fi
        if [ "${'$'}__nrsuite_apply_prompt" = "1" ]; then
            if [ -n "${'$'}BASH_VERSION" ]; then
                export PROMPT_DIRTRIM=2
                export PS1='\[\e[1;92m\]NRSuite\[\e[0m\] \[\e[1;90m\]\w\[\e[0m\] \[\e[1;92m\]\${'$'}\[\e[0m\] '
            elif [ -n "${'$'}ZSH_VERSION" ]; then
                export PROMPT='%F{green}NRSuite%f %F{242}%2~%f %F{green}%(!.#.${'$'})%f '
            else
                export PS1='NRSuite \${'$'} '
            fi
        fi
        stty echo 2>/dev/null
        unset __nrsuite_show_banner __nrsuite_apply_prompt GRAY VERSION_CODE
    """.trimIndent()
}
