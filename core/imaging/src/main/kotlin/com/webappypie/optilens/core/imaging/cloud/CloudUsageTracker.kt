package com.webappypie.optilens.core.imaging.cloud

import android.content.Context
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks and strictly limits cloud AI invocations per calendar month.
 *
 * Guarantees zero unexpected cloud operating costs by strictly capping
 * premium AI calls per billing period.
 */
@Singleton
class CloudUsageTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    private val prefs = context.getSharedPreferences("optilens_cloud_quota", Context.MODE_PRIVATE)

    private val _usedThisMonth = MutableStateFlow(0)
    val usedThisMonth: StateFlow<Int> = _usedThisMonth.asStateFlow()

    init {
        checkAndResetMonthlyQuota()
    }

    private fun currentMonthKey(): String {
        return SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }

    @Synchronized
    fun checkAndResetMonthlyQuota() {
        val storedMonth = prefs.getString(KEY_MONTH, "")
        val currentMonth = currentMonthKey()

        if (storedMonth != currentMonth) {
            // New month started — reset usage counter
            prefs.edit()
                .putString(KEY_MONTH, currentMonth)
                .putInt(KEY_USAGE_COUNT, 0)
                .apply()
            _usedThisMonth.value = 0
            logger.i(TAG, "Reset monthly cloud quota for period: $currentMonth")
        } else {
            val count = prefs.getInt(KEY_USAGE_COUNT, 0)
            _usedThisMonth.value = count
        }
    }

    fun canInvokeCloud(config: CloudEnhancementConfig = CloudEnhancementConfig.DEFAULT): Boolean {
        checkAndResetMonthlyQuota()
        return _usedThisMonth.value < config.maxMonthlyQuota
    }

    fun remainingQuota(config: CloudEnhancementConfig = CloudEnhancementConfig.DEFAULT): Int {
        checkAndResetMonthlyQuota()
        return maxOf(0, config.maxMonthlyQuota - _usedThisMonth.value)
    }

    @Synchronized
    fun recordCloudInvocation(): Int {
        checkAndResetMonthlyQuota()
        val next = prefs.getInt(KEY_USAGE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_USAGE_COUNT, next).apply()
        _usedThisMonth.value = next
        logger.i(TAG, "Recorded cloud AI invocation. Used: $next")
        return next
    }

    companion object {
        private const val TAG = "CloudUsageTracker"
        private const val KEY_MONTH = "quota_month"
        private const val KEY_USAGE_COUNT = "cloud_usage_count"
    }
}
