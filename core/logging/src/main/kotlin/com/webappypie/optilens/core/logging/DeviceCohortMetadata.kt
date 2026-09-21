package com.webappypie.optilens.core.logging

import android.os.Build

/**
 * Non-sensitive device and performance cohort metadata.
 *
 * Privacy Invariants:
 * - Strictly zero personally identifiable information (zero IMEI, Android ID, serial number, MAC address).
 * - Categorizes devices into coarse hardware buckets for performance analysis and regression detection.
 */
data class DeviceCohortMetadata(
    val socFamily: String,
    val ramBucket: String,
    val apiLevel: Int,
    val manufacturerBucket: String,
    val hardwareLevel: String,
    val quirkCount: Int = 0,
) {
    companion object {
        /**
         * Resolves safe, non-identifying cohort metadata for the current runtime.
         */
        fun current(
            apiLevel: Int = Build.VERSION.SDK_INT,
            manufacturer: String = Build.MANUFACTURER.orEmpty(),
            hardware: String = Build.HARDWARE.orEmpty(),
            totalRamGb: Int = 8,
            hardwareLevel: String = "FULL",
            quirkCount: Int = 0,
        ): DeviceCohortMetadata {
            val socFamily = resolveSocFamily(hardware)
            val ramBucket = when {
                totalRamGb <= 3 -> "<=3GB"
                totalRamGb <= 4 -> "4GB"
                totalRamGb <= 6 -> "6GB"
                totalRamGb <= 8 -> "8GB"
                totalRamGb <= 12 -> "12GB"
                else -> ">12GB"
            }
            val mfgBucket = when {
                manufacturer.contains("samsung", ignoreCase = true) -> "Samsung"
                manufacturer.contains("google", ignoreCase = true) -> "Google"
                manufacturer.contains("xiaomi", ignoreCase = true) ||
                    manufacturer.contains("redmi", ignoreCase = true) ||
                    manufacturer.contains("poco", ignoreCase = true) -> "Xiaomi"
                manufacturer.contains("oneplus", ignoreCase = true) -> "OnePlus"
                manufacturer.contains("motorola", ignoreCase = true) -> "Motorola"
                else -> "Other"
            }

            return DeviceCohortMetadata(
                socFamily = socFamily,
                ramBucket = ramBucket,
                apiLevel = apiLevel,
                manufacturerBucket = mfgBucket,
                hardwareLevel = hardwareLevel,
                quirkCount = quirkCount,
            )
        }

        private fun resolveSocFamily(hardware: String): String {
            val hwLower = hardware.lowercase()
            return when {
                hwLower.contains("qcom") || hwLower.contains("qualcomm") || hwLower.contains("sm") -> "Snapdragon"
                hwLower.contains("tensor") || hwLower.contains("gs") -> "Tensor"
                hwLower.contains("mt") || hwLower.contains("mediatek") || hwLower.contains("dimensity") -> "MediaTek"
                hwLower.contains("exynos") || hwLower.contains("universal") -> "Exynos"
                hwLower.contains("unisoc") || hwLower.contains("spreadtrum") || hwLower.contains("ums") -> "Unisoc"
                else -> "Generic"
            }
        }
    }
}
