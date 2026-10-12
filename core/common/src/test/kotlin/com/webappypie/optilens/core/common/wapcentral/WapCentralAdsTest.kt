package com.webappypie.optilens.core.common.wapcentral

import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WapCentralAdsTest {

    /** Trimmed copy of the live `/ads/config` response observed on 2026-10-12. */
    private val liveSample = """
        {"success":true,"requestId":"req_1","appId":"app_1791801351561","service":"ads","version":"v1",
         "data":{"appId":"app_1791801351561","enabled":true,"testMode":true,"version":"ff3fb52923c359cd",
          "safety":{"maxAdRating":"PG","blockedCategories":["violence"]},
          "formats":{
           "banner":{"enabled":true,"format":"banner","refreshIntervalSeconds":60,
             "frequency":{"minIntervalSeconds":30,"cooldownSeconds":0,"sessionCap":100,"dailyCap":500},
             "mediationChain":[{"networkId":"admob","priority":2,"adUnitId":"ca-app-pub-3940256099942544/9214589741","timeoutMs":5000},
                               {"networkId":"custom_ads","priority":6,"adUnitId":"custom_inhouse","timeoutMs":5000}],
             "customAdFallback":null},
           "interstitial":{"enabled":true,"format":"interstitial",
             "frequency":{"minIntervalSeconds":45,"cooldownSeconds":45,"sessionCap":4,"dailyCap":12},
             "mediationChain":[{"networkId":"admob","priority":2,"adUnitId":"ca-app-pub-3940256099942544/1033173712","timeoutMs":5000}]},
           "rewarded_interstitial":{"enabled":false,"format":"rewarded_interstitial","mediationChain":[]}},
          "placements":{}}}
    """.trimIndent()

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
        override val mainImmediate = Dispatchers.Unconfined
    }
    private val creds = WapCentralCredentials("https://example.test/api/platform/v1", "app_1", "key_1")

    @Test
    fun `parses the live schema and picks the AdMob unit`() {
        val config = WapAdsConfigParser.parse(liveSample)!!
        assertTrue(config.enabled)
        assertTrue(config.testMode)
        assertEquals("ca-app-pub-3940256099942544/9214589741", config.adMobUnit(WapAdsConfig.FORMAT_BANNER))
        assertEquals(45, config.frequency(WapAdsConfig.FORMAT_INTERSTITIAL)?.cooldownSeconds)
    }

    @Test
    fun `disabled format, global kill switch and unknown format give no ad`() {
        val config = WapAdsConfigParser.parse(liveSample)!!
        assertNull(config.adMobUnit("rewarded_interstitial"))
        assertNull(config.adMobUnit("native"))
        assertNull(config.copy(enabled = false).adMobUnit(WapAdsConfig.FORMAT_BANNER))
    }

    @Test
    fun `error envelopes and garbage are rejected`() {
        assertNull(WapAdsConfigParser.parse("""{"success":false,"error":{"code":"FORBIDDEN"}}"""))
        assertNull(WapAdsConfigParser.parse("<html>"))
    }

    @Test
    fun `repository sends both auth headers and becomes ready`() = runBlocking {
        var seen: Map<String, String> = emptyMap()
        var url = ""
        val repo = WapCentralAdsRepository(creds, { u, h -> url = u; seen = h; 200 to liveSample }, dispatchers)
        repo.refresh()
        assertEquals("https://example.test/api/platform/v1/ads/config", url)
        assertEquals("app_1", seen["X-App-Id"])
        assertEquals("key_1", seen["X-App-Key"])
        assertTrue(repo.state.value is WapAdsState.Ready)
    }

    @Test
    fun `offline, forbidden and unconfigured builds fail closed`() = runBlocking {
        val offline = WapCentralAdsRepository(creds, { _, _ -> throw IOException("no route") }, dispatchers)
        offline.refresh()
        assertTrue(offline.state.value is WapAdsState.Unavailable)
        assertNull(offline.config)

        val forbidden = WapCentralAdsRepository(creds, { _, _ -> 403 to "{}" }, dispatchers)
        forbidden.refresh()
        assertEquals(WapAdsState.Unavailable("HTTP 403"), forbidden.state.value)

        var called = false
        val unconfigured = WapCentralAdsRepository(creds.copy(appKey = ""), { _, _ -> called = true; 200 to liveSample }, dispatchers)
        unconfigured.refresh()
        assertFalse(called)
        assertTrue(unconfigured.state.value is WapAdsState.Unavailable)
    }

    @Test
    fun `credentials never print the key`() {
        assertFalse(creds.toString().contains("key_1"))
    }
}
