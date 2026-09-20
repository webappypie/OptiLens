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
import com.webappypie.optilens.core.common.monetization.EntitlementRepository

/**
 * Safe banner ad container for non-viewfinder screens (Gallery, Settings).
 *
 * Guaranteed Behavior:
 * 1. Automatically completely removes itself (0dp height) when user has Pro entitlement.
 * 2. Never displays inside the camera viewfinder or near shutter controls.
 * 3. Uses official Google test ad unit IDs in development and debug builds.
 */
@Composable
fun SafeAdBanner(
    placement: AdPlacement,
    entitlementRepository: EntitlementRepository,
    modifier: Modifier = Modifier,
) {
    AdPlacement.validatePlacement(placement)

    val isPro by entitlementRepository.isPro.collectAsStateWithLifecycle(initialValue = false)
    if (isPro) {
        // Pro users see zero ads and zero blank space
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = GoogleMobileAdProvider.TEST_BANNER_AD_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
