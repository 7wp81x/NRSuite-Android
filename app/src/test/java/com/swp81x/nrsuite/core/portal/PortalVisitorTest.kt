package com.swp81x.nrsuite.core.portal

import org.junit.Assert.assertEquals
import org.junit.Test

class PortalVisitorTest {
    @Test
    fun `known mac deduplicates even when ip changes`() {
        val first = PortalVisitor(
            mac = "AA:BB:CC:DD:EE:FF",
            ip = "192.168.4.2",
            userAgent = "Phone",
            requestType = "GET /",
            lastSeen = "10:00:00",
        )
        val second = first.copy(ip = "192.168.4.3", requestType = "POST /login", lastSeen = "10:00:05")

        val merged = mergePortalVisitor(listOf(first), second)

        assertEquals(1, merged.size)
        assertEquals(2, merged.single().visitCount)
        assertEquals("192.168.4.3", merged.single().ip)
        assertEquals("POST /login", merged.single().requestType)
    }

    @Test
    fun `unknown mac update retains mac learned for same ip`() {
        val known = PortalVisitor(
            mac = "AA:BB:CC:DD:EE:FF",
            ip = "192.168.4.2",
            lastSeen = "10:00:00",
        )

        val merged = mergePortalVisitor(
            listOf(known),
            PortalVisitor(mac = "unknown", ip = "192.168.4.2", lastSeen = "10:00:05"),
        )

        assertEquals(1, merged.size)
        assertEquals("AA:BB:CC:DD:EE:FF", merged.single().mac)
        assertEquals(2, merged.single().visitCount)
    }

    @Test
    fun `unknown mac falls back to ip identity and list stays bounded`() {
        val visitors = listOf(
            PortalVisitor(mac = "unknown", ip = "192.168.4.2", lastSeen = "10:00:00"),
            PortalVisitor(mac = "unknown", ip = "192.168.4.3", lastSeen = "10:00:01"),
        )

        val repeated = mergePortalVisitor(
            visitors,
            PortalVisitor(mac = "unknown", ip = "192.168.4.2", lastSeen = "10:00:02"),
            limit = 2,
        )
        assertEquals(2, repeated.size)
        assertEquals(2, repeated.last().visitCount)

        val bounded = mergePortalVisitor(
            repeated,
            PortalVisitor(mac = "unknown", ip = "192.168.4.4", lastSeen = "10:00:03"),
            limit = 2,
        )
        assertEquals(2, bounded.size)
        assertEquals("192.168.4.4", bounded.last().ip)
        assertEquals("192.168.4.2", bounded.first().ip)
    }
}
