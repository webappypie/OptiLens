package com.webappypie.optilens.core.common.config

import com.webappypie.optilens.core.common.feature.FeatureFlags
import com.webappypie.optilens.core.common.feature.LocalFeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository contract for Remote Config, feature kill switches, and hardware parameter overrides.
 *
 * Guarantees 100% offline availability: if remote connectivity is unavailable or unconfigured,
 * conservative local defaults are served immediately without blocking application startup.
 */
interface RemoteConfigRepository {
    /** Reactive stream of active feature flags and remote parameters. */
    val featureFlags: StateFlow<FeatureFlags>

    /** Fetches latest remote configuration and activates updates. Returns true if refreshed. */
    suspend fun fetchAndActivate(): Boolean

    /** Resolves device-specific processing overrides for a given Android hardware model name. */
    fun getDeviceProcessingOverride(deviceModel: String): DeviceProcessingOverride?
}

/**
 * Concrete implementation providing local compile-time defaults and runtime override support.
 */
@Singleton
class LocalRemoteConfigRepository @Inject constructor() : RemoteConfigRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _featureFlags = MutableStateFlow<FeatureFlags>(LocalFeatureFlags)
    override val featureFlags: StateFlow<FeatureFlags> = _featureFlags.asStateFlow()

    override suspend fun fetchAndActivate(): Boolean {
        // In local/offline mode, local defaults remain active
        return true
    }

    /**
     * Updates active flags (useful for remote sync, testing, or debug overrides).
     */
    fun updateFlags(flags: FeatureFlags) {
        _featureFlags.value = flags
    }

    override fun getDeviceProcessingOverride(deviceModel: String): DeviceProcessingOverride? {
        val jsonStr = featureFlags.value.deviceSpecificOverridesJson ?: return null
        return try {
            val config = json.decodeFromString<DeviceOverridesConfig>(jsonStr)
            config.rules.firstOrNull { rule ->
                try {
                    Regex(rule.deviceModelPattern, RegexOption.IGNORE_CASE).matches(deviceModel)
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
