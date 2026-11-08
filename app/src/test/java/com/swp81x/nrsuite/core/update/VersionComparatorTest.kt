package com.swp81x.nrsuite.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun newerPatchVersionDetected() {
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun olderVersionRejected() {
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun releaseBeatsPreRelease() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-beta.2"))
    }

    @Test
    fun preReleaseOrdering() {
        assertTrue(VersionComparator.isNewer("1.0.0-beta.3", "1.0.0-beta.2"))
        assertFalse(VersionComparator.isNewer("1.0.0-beta.2", "1.0.0-beta.2"))
    }

    @Test
    fun releaseCandidateOrdering() {
        assertTrue(VersionComparator.isNewer("1.0.0-rc.1", "1.0.0-beta.3.2.0"))
        assertTrue(VersionComparator.isNewer("1.0.0-rc.2", "1.0.0-rc.1"))
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-rc.1"))
        assertFalse(VersionComparator.isNewer("1.0.0-rc.1", "1.0.0"))
        assertTrue(VersionComparator.isNewer("v1.0.0-rc.1", "1.0.0-beta.3.2.0"))
    }

    @Test
    fun leadingVAndBuildMetadataIgnored() {
        assertTrue(VersionComparator.isNewer("v1.2.0+42", "1.1.9"))
    }
}
