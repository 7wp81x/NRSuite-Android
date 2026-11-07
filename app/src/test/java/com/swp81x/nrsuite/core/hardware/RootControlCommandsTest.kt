package com.swp81x.nrsuite.core.hardware

import org.junit.Assert.*
import org.junit.Test

class RootControlCommandsTest {
    @Test fun allActionsRejectNonRootBeforeDoingWork() {
        RootAction.entries.forEach { action ->
            val script = RootControlCommands.command(action)
            assertTrue(action.name, script.startsWith("[ \"\$(id -u)\" = 0 ]"))
            assertTrue(action.name, script.indexOf("exit 13") < script.indexOf("NRSUITE_ROOT_OK"))
        }
    }

    @Test fun chrootStopRefusesBusyProcessesBeforeAnyUnmountAndNeverDeletesOrKills() {
        val script = RootControlCommands.command(RootAction.CHROOT_STOP)
        assertTrue(script.indexOf("exit 23") < script.indexOf("umount "))
        assertFalse(script.contains("rm "))
        assertFalse(script.contains("kill "))
        assertFalse(script.contains("umount -"))
        assertTrue(script.contains("/data/local/nhsystem/*"))
    }

    @Test fun serviceMutationsRequireVisibleEffectsAndAreLimitedToKnownServices() {
        val actions = RootAction.entries.filter { it.name.matches(Regex("(SSH|APACHE|POSTGRES)_(START|STOP)")) }
        assertEquals(6, actions.size)
        actions.forEach { assertFalse(it.effect.isNullOrBlank()) }
        assertTrue(RootControlCommands.command(RootAction.SSH_START).contains("'service ssh start'"))
        assertTrue(RootControlCommands.command(RootAction.APACHE_STOP).contains("'service apache2 stop'"))
    }

    @Test fun kaliShellVariablesStayQuotedForTheKaliShell() {
        val script = RootControlCommands.command(RootAction.KALI_TOOLS)
        assertTrue(script.contains("/bin/sh -c 'for tool"))
        assertTrue(script.contains("command -v \$tool"))
        assertTrue(script.contains("/usr/bin/env -i"))
    }
}
