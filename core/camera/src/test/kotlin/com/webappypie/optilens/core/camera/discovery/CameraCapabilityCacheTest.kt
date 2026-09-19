package com.webappypie.optilens.core.camera.discovery

import com.webappypie.optilens.core.camera.CameraTestDispatchers
import com.webappypie.optilens.core.camera.fixtures.CameraCapabilityFixtures
import com.webappypie.optilens.core.common.build.FakeBuildInfo
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CameraCapabilityCacheTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var cache: CameraCapabilityCache
    private lateinit var buildInfo: FakeBuildInfo

    @Before
    fun setUp() {
        buildInfo = FakeBuildInfo(versionCode = 100)
        cache = CameraCapabilityCache(
            testDir = tmpFolder.newFolder("camera_cache"),
            buildInfo = buildInfo,
            dispatchers = CameraTestDispatchers(),
            logger = NoOpLogger(),
        ).apply {
            customFingerprint = "test_fingerprint_v1"
        }
    }

    @Test
    fun `getCachedProfile returns null when cache is empty`() = runTest {
        val cached = cache.getCachedProfile()
        assertNull(cached)
    }

    @Test
    fun `saveProfile and getCachedProfile retrieves cached data`() = runTest {
        val profile = CameraCapabilityFixtures.createFlagshipProfile()
        cache.saveProfile(profile)

        val retrieved = cache.getCachedProfile()
        assertNotNull(retrieved)
        assertEquals(profile.deviceModel, retrieved?.deviceModel)
        assertEquals(profile.performanceTier, retrieved?.performanceTier)
        assertEquals(profile.cameras.size, retrieved?.cameras?.size)
    }

    @Test
    fun `fingerprint mismatch invalidates cache`() = runTest {
        val profile = CameraCapabilityFixtures.createFlagshipProfile()
        cache.saveProfile(profile)

        // Simulate OS firmware update
        cache.customFingerprint = "test_fingerprint_v2_updated"

        val retrieved = cache.getCachedProfile()
        assertNull("Cache should have been invalidated due to firmware fingerprint mismatch", retrieved)
    }

    @Test
    fun `clearCache removes persisted file`() = runTest {
        val profile = CameraCapabilityFixtures.createFlagshipProfile()
        cache.saveProfile(profile)

        assertNotNull(cache.getCachedProfile())

        cache.clearCache()
        assertNull(cache.getCachedProfile())
    }
}
