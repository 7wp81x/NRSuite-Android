package com.swp81x.nrsuite.core.terminal

/**
 * App-controlled terminal startup text. These helpers are deliberately pure so
 * the shell prompt and banner can be exercised by JVM unit tests.
 *
 * Nothing here is persisted to the remote shell's configuration. The prompt is
 * exported into a single new interactive shell and disappears when it exits.
 */
internal object TerminalStartup {
    private const val ESC = "\u001B"
    private const val RESET = "$ESC[0m"
    private const val BOLD_BRIGHT_GREEN = "$ESC[1;92m"
    private const val BOLD_BRIGHT_BLACK = "$ESC[1;90m"

    /**
     * A compact, mobile-friendly prompt. `\w` keeps the working directory,
     * `PROMPT_DIRTRIM=2` shortens deeply nested paths, and `\$` renders `$` for
     * a normal user and `#` for root.
     */
    val promptCommand: String =
        "if [ -n \"\$BASH_VERSION\" ]; then " +
            "export PROMPT_DIRTRIM=2; " +
            "export PS1='\\[\\e[1;92m\\]NRSuite\\[\\e[0m\\] " +
            "\\[\\e[1;90m\\]\\w\\[\\e[0m\\] " +
            "\\[\\e[1;92m\\]\\\$\\[\\e[0m\\] '; " +
            "fi"

    fun banner(versionName: String): String = buildString {
        append(RESET)
        append(" v")
        append(versionName)
        append('\n')
        append(BOLD_BRIGHT_GREEN)
        append(" _   _ ____  ____        _ _       \n")
        append("| \\ | |  _ \\/ ___| _   _(_) |_ ___ \n")
        append("|  \\| | |_) \\___ \\| | | | | __/ _ \\\n")
        append("| |\\  |  _ < ___) | |_| | | ||  __/\n")
        append("|_| \\_|_| \\_\\____/ \\__,_|_|\\__\\___|\n")
        append(BOLD_BRIGHT_BLACK)
        append(" [ Developed by 7wp81x ]\n\n")
        append(RESET)
    }

    fun isLocalTermuxHost(host: String): Boolean {
        val normalized = host.trim()
        return normalized == "127.0.0.1" || normalized.equals("localhost", ignoreCase = true)
    }
}
