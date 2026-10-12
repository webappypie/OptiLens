package com.webappypie.optilens.core.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.compose.runtime.key
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.wapcentral.WapAdsConfig
import com.webappypie.optilens.core.common.wapcentral.WapAdsState
import com.webappypie.optilens.core.common.wapcentral.WapCentralAdsRepository

/**
 * Safe banner ad container for non-viewfinder screens (Gallery, Settings).
 *
 * Guaranteed Behavior:
 * 1. Automatically completely removes itself (0dp height) when user has Pro entitlement.
 * 2. Never displays inside the camera viewfinder or near shutter controls.
 * 3. Shows an ad only when WAPCentral's `/ads/config` enables ads and the banner format;
 *    the ad unit comes from that config (no hardcoded IDs). Unknown/offline/disabled → nothing.
 */
@Composable
fun SafeAdBanner(
    placement: AdPlacement,
    entitlementRepository: EntitlementRepository,
    adsRepository: WapCentralAdsRepository,
    modifier: Modifier = Modifier,
) {
    AdPlacement.validatePlacement(placement)

    val isPro by entitlementRepository.isPro.collectAsStateWithLifecycle(initialValue = false)
    if (isPro) {
        // Pro users see zero ads and zero blank space
        return
    }
    val adsState by adsRepository.state.collectAsStateWithLifecycle()
    val adUnitId = (adsState as? WapAdsState.Ready)?.config?.adMobUnit(WapAdsConfig.FORMAT_BANNER) ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center,
    ) {
        key(adUnitId) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        this.adUnitId = adUnitId
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
