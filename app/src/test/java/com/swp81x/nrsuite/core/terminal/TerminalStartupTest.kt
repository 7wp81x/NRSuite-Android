package com.swp81x.nrsuite.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalStartupTest {
    @Test
    fun bannerUsesBuildVersionAndResetsTerminalStyle() {
        val banner = TerminalStartup.banner("1.0.0-beta.3.2.0")

        assertTrue(banner.contains("v1.0.0-beta.3.2.0"))
        assertTrue(banner.contains(" _   _ ____  ____"))
        assertTrue(banner.contains("Developed by 7wp81x"))
        assertTrue(banner.startsWith("\u001B[0m"))
        assertTrue(banner.endsWith("\u001B[0m"))
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
