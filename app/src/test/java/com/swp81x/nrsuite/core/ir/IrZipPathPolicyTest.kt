package com.swp81x.nrsuite.core.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrZipPathPolicyTest {
    @Test
    fun acceptsSafeNestedIrPaths() {
        assertEquals(
            listOf("TVs", "Samsung", "Power.ir"),
            IrZipPathPolicy.safeSegments("TVs/Samsung/Power.ir"),
        )
        assertTrue(
            IrZipPathPolicy.isIrFile(
                IrZipPathPolicy.safeSegments("AC/Cool.IR").orEmpty(),
            ),
        )
    }

    @Test
    fun rejectsZipSlipAndAmbiguousPaths() {
        assertNull(IrZipPathPolicy.safeSegments("../escape.ir"))
        assertNull(IrZipPathPolicy.safeSegments("TVs/../../escape.ir"))
        assertNull(IrZipPathPolicy.safeSegments("/absolute.ir"))
        assertNull(IrZipPathPolicy.safeSegments("TVs\\Samsung\\Power.ir"))
        assertNull(IrZipPathPolicy.safeSegments("TVs//Power.ir"))
    }

    @Test
    fun rejectsExcessiveDepthAndPathLength() {
        val deep = (1..20).joinToString("/") { "dir$it" } + "/Power.ir"
        assertNull(IrZipPathPolicy.safeSegments(deep))
        val long = "a".repeat(250) + "/Power.ir"
        assertNull(IrZipPathPolicy.safeSegments(long))
    }

    @Test
    fun identifiesFlipperArchiveRoot() {
        val paths = listOf(
            listOf("Flipper-IRDB-main", "TVs", "Samsung", "Power.ir"),
            listOf("Flipper-IRDB-main", "AC", "Generic", "Cool.ir"),
        )
        assertEquals("Flipper-IRDB-main", IrZipPathPolicy.commonFlipperRoot(paths))
        assertNull(
            IrZipPathPolicy.commonFlipperRoot(
                listOf(
                    listOf("TVs", "Samsung", "Power.ir"),
                    listOf("AC", "Generic", "Cool.ir"),
                ),
            ),
        )
    }

    @Test
    fun ignoresNonIrFiles() {
        assertFalse(IrZipPathPolicy.isIrFile(listOf("README.md")))
    }
}
