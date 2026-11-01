package com.swp81x.nrsuite.core.credentials

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiQrPayloadTest {
    @Test
    fun buildsStandardWifiQrPayload() {
        assertEquals(
            "WIFI:T:WPA;S:HomeNet;P:correct horse;H:false;;",
            WifiQrPayload.build("HomeNet", "correct horse"),
        )
    }

    @Test
    fun escapesStructuralCharacters() {
        assertEquals(
            "WIFI:T:WPA;S:Lab\\;5G\\,A;P:p\\:a\\\\ss\\\"word;H:true;;",
            WifiQrPayload.build(
                ssid = "Lab;5G,A",
                password = "p:a\\ss\"word",
                hidden = true,
            ),
        )
    }
}
