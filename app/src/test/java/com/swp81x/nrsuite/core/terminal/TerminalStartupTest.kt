package com.swp81x.nrsuite.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TerminalStartupTest {
    @Test
    fun bannerUsesBuildVersionAndResetsTerminalStyle() {
        val banner = TerminalStartup.banner("1.0.0-beta.3.2.0")

        assertTrue(banner.contains("v1.0.0-beta.3.2.0"))
        assertTrue(banner.contains(" _   _ ____  ____"))
        assertTrue(banner.contains("Developed by 7wp81x"))
        assertTrue(banner.startsWith("\u001B[0m"))
        assertTrue(banner.endsWith("\u001B[0m"))
        assertTrue(banner.contains("\r\n"))
    }

    @Test
    fun promptUsesShortPathAndBashRootIndicator() {
        val command = TerminalStartup.promptCommand

        assertTrue(command.contains("PROMPT_DIRTRIM=2"))
        assertTrue(command.contains("NRSuite"))
        assertTrue(command.contains("\\w"))
        assertTrue(command.contains("\\$"))
        assertTrue(command.contains("BASH_VERSION"))
    }

    @Test
    fun startupCommandClearsThenPrintsAndSetsPromptWithoutRawNewlines() {
        val command = TerminalStartup.startupCommand(
            versionName = "1.0.0-beta.3.2.0",
            showBanner = true,
            applyPrompt = true,
        ) ?: error("startup command was null")

        assertTrue(command.startsWith("stty -echo"))
        assertTrue(command.contains("printf '\\033[2J\\033[H'"))
        assertTrue(command.contains("printf '%b'"))
        assertTrue(command.contains("export PS1="))
        assertTrue(command.endsWith("stty echo 2>/dev/null"))
        assertFalse(command.contains("\n"))
        assertFalse(command.contains("\r"))
    }

    @Test
    fun generatedScriptContainsAutomaticVersionBannerAndPrompt() {
        val script = TerminalStartup.script("1.0.0-beta.3.2.0")

        assertTrue(script.startsWith("#!/usr/bin/env bash"))
        assertTrue(script.contains("v%s"))
        assertTrue(script.contains("1.0.0-beta.3.2.0"))
        assertTrue(script.contains("printf '\\033[2J\\033[H'"))
        assertTrue(script.contains("PROMPT_DIRTRIM=2"))
        assertTrue(script.contains("__nrsuite_show_banner"))
        assertTrue(script.contains("__nrsuite_apply_prompt"))
    }

    @Test
    fun generatedScriptParsesAsBashWhenAvailable() {
        val bash = listOf(File("/bin/bash"), File("/usr/bin/bash"))
            .firstOrNull { it.canExecute() }
            ?: return
        val scriptFile = File.createTempFile("nrsuite-terminal", ".sh")
        try {
            scriptFile.writeText(TerminalStartup.script("1.0.0-beta.3.2.0"))
            val process = ProcessBuilder(bash.absolutePath, "-n", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.waitFor())
        } finally {
            scriptFile.delete()
        }
    }

    @Test
    fun generatedScriptRendersBannerAndSetsPromptWhenBashIsAvailable() {
        val bash = listOf(File("/bin/bash"), File("/usr/bin/bash"))
            .firstOrNull { it.canExecute() }
            ?: return
        val scriptFile = File.createTempFile("nrsuite-terminal", ".sh")
        try {
            scriptFile.writeText(TerminalStartup.script("1.0.0-beta.3.2.0"))
            val quotedScript = scriptFile.absolutePath.replace("'", "'\\''")
            val command = ". '$quotedScript' 1 1; " +
                "printf '__NRSUITE_PROMPT__%s\\n' \"\$PS1\""
            val process = ProcessBuilder(bash.absolutePath, "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.waitFor())
            assertTrue(output.contains("v1.0.0-beta.3.2.0"))
            assertTrue(output.contains("_   _ ____  ____"))
            assertTrue(output.contains("NRSuite"))
            assertTrue(output.contains("\\w"))
        } finally {
            scriptFile.delete()
        }
    }

    @Test
    fun executeCommandSourcesRemoteScriptOrUsesInlineFallback() {
        val command = TerminalStartup.executeCommand(
            versionName = "1.0.0-beta.3.2.0",
            showBanner = true,
            applyPrompt = true,
            useRemoteScript = true,
        ) ?: error("execute command was null")

        assertTrue(command.contains("\$HOME/.NRSuite"))
        assertTrue(command.contains(". \"\$HOME/.NRSuite\" 1 1"))
        assertTrue(command.contains("sha256sum"))
        assertTrue(command.contains("cut -d' ' -f1"))
        assertTrue(command.contains("printf '%b'"))
        assertFalse(command.contains("\n"))
    }

    @Test
    fun missingRemoteScriptFallsBackToInlineCommand() {
        val command = TerminalStartup.executeCommand(
            versionName = "1.0.0-beta.3.2.0",
            showBanner = true,
            applyPrompt = false,
            useRemoteScript = false,
        ) ?: error("execute command was null")

        assertFalse(command.contains(".NRSuite"))
        assertTrue(command.contains("printf '%b'"))
        assertTrue(command.contains("stty -echo"))
    }

    @Test
    fun startupCommandIsSkippedWhenBothOptionsAreOff() {
        assertEquals(
            null,
            TerminalStartup.startupCommand(
                versionName = "1.0.0-beta.3.2.0",
                showBanner = false,
                applyPrompt = false,
            ),
        )
    }

    @Test
    fun promptIsLimitedToLocalTermuxHosts() {
        assertTrue(TerminalStartup.isLocalTermuxHost("127.0.0.1"))
        assertTrue(TerminalStartup.isLocalTermuxHost("localhost"))
        assertTrue(TerminalStartup.isLocalTermuxHost("  LOCALHOST  "))
        assertFalse(TerminalStartup.isLocalTermuxHost("192.168.1.10"))
        assertFalse(TerminalStartup.isLocalTermuxHost("example.com"))
    }

    @Test
    fun startupSettingsDefaultsAreBannerOnAndPromptOff() {
        val settings = TerminalUiSettings()

        assertTrue(settings.showStartupBanner)
        assertFalse(settings.useNrSuitePrompt)
    }

    @Test
    fun bannerAndPromptDoNotPersistShellConfiguration() {
        val command = TerminalStartup.promptCommand

        assertTrue(command.contains("export PS1="))
        assertFalse(command.contains(".bashrc"))
        assertFalse(command.contains(".profile"))
        assertFalse(command.contains(">>"))
    }
}
