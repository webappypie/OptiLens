package com.webappypie.optilens.core.ui.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.webappypie.optilens.core.common.config.RemoteConfigRepository
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permitted safe ad placement locations.
 *
 * CRITICAL PRODUCT PRINCIPLE:
 * In accordance with Phase 17 requirements and user trust:
 * Viewfinder and shutter button placements are STRICTLY FORBIDDEN.
 * Ads may only appear in non-capture screens (Gallery, Settings, Photo Review).
 */
enum class AdPlacement {
    GALLERY_BOTTOM_BANNER,
    SETTINGS_UPGRADE_BANNER,
    REVIEW_POST_SAVE_INTERSTITIAL,
    AI_TOOLS_PREVIEW_REWARDED;

    companion object {
        fun validatePlacement(placement: AdPlacement) {
            check(placement.name != "VIEWFINDER" && !placement.name.contains("SHUTTER")) {
                "Ads are strictly forbidden in the viewfinder or near the shutter"
            }
        }
    }
}

/**
 * Frequency cap and pacing governor for advertising displays.
 *
 * Enforces:
 * 1. Minimum elapsed interval between full-screen ads (default: 300 seconds).
 * 2. Minimum photo captures before an interstitial is eligible (default: 3 captures).
 * 3. Immediate suppression if user holds Pro entitlement.
 */
@Singleton
class AdFrequencyCapManager @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val entitlementRepository: EntitlementRepository,
) {
    private var lastAdDisplayTimestampMs = 0L
    private var capturesSinceLastAd = 0

    fun recordCapture() {
        capturesSinceLastAd++
    }

    fun canShowInterstitial(): Boolean {
        // 1. Pro users never see ads
        if (entitlementRepository.isPro.value) return false

        val flags = remoteConfigRepository.featureFlags.value
        // 2. Global kill switch
        if (!flags.adsEnabled) return false

        // 3. Minimum captures condition
        if (capturesSinceLastAd < flags.adMinCapturesBetweenInterstitials) return false

        // 4. Time interval condition
        val elapsedSec = (System.currentTimeMillis() - lastAdDisplayTimestampMs) / 1000L
        return elapsedSec >= flags.adFrequencyIntervalSec
    }

    fun recordAdShown() {
        lastAdDisplayTimestampMs = System.currentTimeMillis()
        capturesSinceLastAd = 0
    }

    fun reset() {
        lastAdDisplayTimestampMs = 0L
        capturesSinceLastAd = 0
    }
}

/**
 * Abstraction governing ad operations across the application.
 */
interface AdProvider {
    val isInitialized: Boolean
    fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit)
    fun isInterstitialReady(): Boolean
    fun recordImpression(placement: AdPlacement)
    fun recordClick(placement: AdPlacement)
}

/**
 * Google Mobile Ads implementation driven by WAPCentral `/ads/config`: ad units, format
 * enablement and the global kill switch come from WAPCentral (test units in its testing
 * mode). Nothing is preloaded; no ad is requested unless WAPCentral enables the format.
 */
@Singleton
class GoogleMobileAdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementRepository: EntitlementRepository,
    private val frequencyCapManager: AdFrequencyCapManager,
    private val adsRepository: com.webappypie.optilens.core.common.wapcentral.WapCentralAdsRepository,
    private val logger: AppLogger,
) : AdProvider {

    companion object {
        private const val TAG = "GoogleMobileAdProvider"
    }

    private fun interstitialUnit(): String? =
        adsRepository.config?.adMobUnit(com.webappypie.optilens.core.common.wapcentral.WapAdsConfig.FORMAT_INTERSTITIAL)

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var _isInitialized = false
    override val isInitialized: Boolean get() = _isInitialized

    init {
        try {
            MobileAds.initialize(context) { status ->
                _isInitialized = true
                logger.d(TAG, "MobileAds initialized: $status")
            }
        } catch (e: Exception) {
            logger.e(TAG, "MobileAds initialization error", e)
        }

        // Listen for Pro status changes to purge cached ads immediately
        CoroutineScope(Dispatchers.Main.immediate).launch {
            entitlementRepository.isPro.collect { isPro ->
                if (isPro) {
                    interstitialAd = null
                    logger.d(TAG, "User is Pro: purging cached ad instances")
                }
            }
        }
    }

    private fun loadInterstitialIfEligible() {
        if (entitlementRepository.isPro.value || isLoadingInterstitial || interstitialAd != null) {
            return
        }
        val unit = interstitialUnit() ?: return

        isLoadingInterstitial = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            unit,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    logger.d(TAG, "Interstitial ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    logger.w(TAG, "Interstitial ad failed to load: ${error.message}")
                }
            }
        )
    }

    override fun isInterstitialReady(): Boolean {
        if (entitlementRepository.isPro.value || interstitialUnit() == null) return false
        return interstitialAd != null && frequencyCapManager.canShowInterstitial()
    }

    override fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit) {
        AdPlacement.validatePlacement(AdPlacement.REVIEW_POST_SAVE_INTERSTITIAL)

        if (entitlementRepository.isPro.value || interstitialUnit() == null || !frequencyCapManager.canShowInterstitial()) {
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    frequencyCapManager.recordAdShown()
                    loadInterstitialIfEligible()
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    loadInterstitialIfEligible()
                    onDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    logger.d(TAG, "Interstitial ad showed")
                }
            }
            ad.show(activity)
        } else {
            loadInterstitialIfEligible()
            onDismissed()
        }
    }

    override fun recordImpression(placement: AdPlacement) {
        AdPlacement.validatePlacement(placement)
        logger.d(TAG, "Ad impression: $placement")
    }

    override fun recordClick(placement: AdPlacement) {
        AdPlacement.validatePlacement(placement)
        logger.d(TAG, "Ad clicked: $placement")
    }
}

/**
 * No-Op AdProvider for Pro users, test environments, or when ads are disabled.
 */
class NoOpAdProvider : AdProvider {
    override val isInitialized: Boolean = true
    override fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit) = onDismissed()
    override fun isInterstitialReady(): Boolean = false
    override fun recordImpression(placement: AdPlacement) = Unit
    override fun recordClick(placement: AdPlacement) = Unit
}
