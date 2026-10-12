package com.webappypie.optilens.core.common.wapcentral

import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WAPCentral app identity (`WAPCentral_OptiLens_Integration_Guide.md`). The key comes from
 * `local.properties` / CI secrets at build time and is never committed or logged.
 */
data class WapCentralCredentials(
    val baseUrl: String,
    val appId: String,
    val appKey: String,
) {
    val isConfigured: Boolean get() = baseUrl.startsWith("https://") && appId.isNotBlank() && appKey.isNotBlank()

    override fun toString() = "WapCentralCredentials(baseUrl=$baseUrl, appId=$appId, appKey=<redacted>)"
}

// ── /ads/config response (schema observed from the live endpoint, 2026-10-12) ──

@Serializable
internal data class WapAdsEnvelope(val success: Boolean = false, val data: WapAdsConfig? = null)

@Serializable
data class WapAdsConfig(
    val enabled: Boolean = false,
    val testMode: Boolean = true,
    val version: String = "",
    val formats: Map<String, WapAdFormat> = emptyMap(),
) {
    /**
     * The AdMob ad unit for [format] if WAPCentral enables both ads and that format; null
     * otherwise (the placement must then show nothing). Other networks in the chain
     * (e.g. `custom_ads` house ads) have no client contract yet and are skipped.
     */
    fun adMobUnit(format: String): String? {
        if (!enabled) return null
        val f = formats[format]?.takeIf { it.enabled } ?: return null
        return f.mediationChain
            .filter { it.networkId == NETWORK_ADMOB && it.adUnitId.startsWith("ca-app-pub-") }
            .minByOrNull { it.priority }
            ?.adUnitId
    }

    fun frequency(format: String): WapAdFrequency? = formats[format]?.frequency

    companion object {
        const val NETWORK_ADMOB = "admob"
        const val FORMAT_BANNER = "banner"
        const val FORMAT_INTERSTITIAL = "interstitial"
    }
}

@Serializable
data class WapAdFormat(
    val enabled: Boolean = false,
    val refreshIntervalSeconds: Int? = null,
    val frequency: WapAdFrequency? = null,
    val mediationChain: List<WapMediationEntry> = emptyList(),
)

@Serializable
data class WapAdFrequency(
    val minIntervalSeconds: Int = 0,
    val cooldownSeconds: Int = 0,
    val sessionCap: Int = 0,
    val dailyCap: Int = 0,
)

@Serializable
data class WapMediationEntry(
    val networkId: String,
    val priority: Int = 0,
    val adUnitId: String = "",
    val timeoutMs: Long = 5_000,
)

object WapAdsConfigParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses an `/ads/config` body; null for errors, `success:false` or malformed JSON. */
    fun parse(body: String): WapAdsConfig? =
        runCatching { json.decodeFromString(WapAdsEnvelope.serializer(), body) }
            .getOrNull()
            ?.takeIf { it.success }
            ?.data
}

sealed interface WapAdsState {
    /** Not fetched yet: no ads are shown. */
    data object Unknown : WapAdsState
    data class Ready(val config: WapAdsConfig) : WapAdsState
    /** Offline, not configured, or rejected: no ads are shown (fail closed, app unaffected). */
    data class Unavailable(val reason: String) : WapAdsState
}

/** Minimal HTTPS GET with WAPCentral auth headers. Separate so tests can replace it. */
fun interface WapCentralHttp {
    /** @return status code to body. */
    fun get(url: String, headers: Map<String, String>): Pair<Int, String>
}

class UrlConnectionWapCentralHttp @Inject constructor() : WapCentralHttp {
    override fun get(url: String, headers: Map<String, String>): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.useCaches = false
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            // A single read() may return only the first network chunk: read until EOF (capped).
            val body = stream?.bufferedReader()?.use { reader ->
                val out = StringBuilder()
                val chunk = CharArray(8 * 1024)
                while (out.length < MAX_BODY_CHARS) {
                    val n = reader.read(chunk)
                    if (n < 0) break
                    out.append(chunk, 0, n)
                }
                out.toString()
            } ?: ""
            return code to body
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_BODY_CHARS = 256 * 1024
    }
}

/**
 * Effective ads configuration from WAPCentral (`GET /ads/config`), the control plane for
 * whether ads run, which formats are enabled, the ad units and frequency limits.
 * Kept in memory only (the guide forbids a local duplicate of WAPCentral configuration);
 * fetched once per process and on demand. Any failure means "no ads", never an error to
 * the user and never a blocked camera/edit/save flow.
 */
@Singleton
class WapCentralAdsRepository @Inject constructor(
    private val credentials: WapCentralCredentials,
    private val http: WapCentralHttp,
    private val dispatchers: AppDispatchers,
) {
    private val _state = MutableStateFlow<WapAdsState>(WapAdsState.Unknown)
    val state: StateFlow<WapAdsState> = _state.asStateFlow()

    val config: WapAdsConfig? get() = (_state.value as? WapAdsState.Ready)?.config

    suspend fun refresh() {
        if (!credentials.isConfigured) {
            _state.value = WapAdsState.Unavailable("WAPCentral credentials not configured for this build")
            return
        }
        _state.value = withContext(dispatchers.io) {
            try {
                val (code, body) = http.get(
                    url = credentials.baseUrl.trimEnd('/') + "/ads/config",
                    headers = mapOf(
                        "X-App-Id" to credentials.appId,
                        "X-App-Key" to credentials.appKey,
                        "Accept" to "application/json",
                    ),
                )
                val parsed = if (code == 200) WapAdsConfigParser.parse(body) else null
                when {
                    parsed != null -> WapAdsState.Ready(parsed)
                    code == 200 -> WapAdsState.Unavailable("Unrecognised ads config")
                    else -> WapAdsState.Unavailable("HTTP $code")
                }
            } catch (e: IOException) {
                WapAdsState.Unavailable("Network unavailable")
            } catch (e: Exception) {
                WapAdsState.Unavailable(e.javaClass.simpleName)
            }
        }
    }
}
