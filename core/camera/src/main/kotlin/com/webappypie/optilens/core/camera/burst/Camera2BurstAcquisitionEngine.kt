package com.webappypie.optilens.core.camera.burst

import android.hardware.camera2.CaptureResult
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.webappypie.optilens.core.camera.analysis.motion.GyroMotionTracker
import com.webappypie.optilens.core.camera.burst.model.BurstDiagnostics
import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.model.GyroSample
import com.webappypie.optilens.core.camera.burst.model.GyroWindow
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Production implementation of [BurstAcquisitionEngine] utilizing Camera2 interop
 * and CameraX [ImageCapture].
 *
 * Enforces memory safety using [BoundedBufferPool], non-blocking capture execution,
 * timeout/cancellation protection, and automatic fallback to single-frame capture.
 */
class Camera2BurstAcquisitionEngine(
    private val imageCaptureProvider: () -> ImageCapture?,
    private val cameraControlProvider: () -> CameraControl?,
    private val gyroMotionTracker: GyroMotionTracker,
    private val bufferPool: BoundedBufferPool = BoundedBufferPool(maxCapacity = 12),
    private val captureExecutor: Executor,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : BurstAcquisitionEngine {

    private val isCancelled = AtomicBoolean(false)

    override suspend fun acquireBurst(
        frameCount: Int,
        evOffsets: List<Int>,
        mode: CaptureStrategyMode,
        targetRotation: Int,
        timeoutMs: Long,
        onProgress: ((completed: Int, total: Int) -> Unit)?,
    ): OptiResult<BurstResult> = withContext(dispatchers.io) {
        val imageCapture = imageCaptureProvider() ?: return@withContext OptiResult.Error(
            OptiError.CameraUnavailable("ImageCapture use case is uninitialized")
        )
        val cameraControl = cameraControlProvider()

        val actualCount = frameCount.coerceAtLeast(1)
        val validEvOffsets = if (evOffsets.isEmpty()) listOf(0) else evOffsets

        isCancelled.set(false)
        val burstStartTime = System.currentTimeMillis()
        val packets = mutableListOf<FramePacket>()
        val interFrameIntervals = mutableListOf<Long>()
        var lastFrameTime = burstStartTime

        try {
            withTimeout(timeoutMs) {
                // Ensure gyro tracking is running during the exposure sequence
                gyroMotionTracker.start()

                for (i in 0 until actualCount) {
                    coroutineContext.ensureActive()
                    if (isCancelled.get()) throw CancellationException("Burst cancelled by caller")

                    // 1. Apply EV offset for bracketed HDR if needed
                    val ev = validEvOffsets[i % validEvOffsets.size]
                    if (ev != 0 && cameraControl != null) {
                        try {
                            cameraControl.setExposureCompensationIndex(ev)
                        } catch (e: Exception) {
                            logger.w(TAG, "Failed setting EV offset $ev: ${e.message}")
                        }
                    }

                    val gyroStartNs = System.nanoTime()
                    val frameGyroSamples = mutableListOf<GyroSample>()

                    // Record initial gyro sample
                    val initialVelocity = gyroMotionTracker.angularVelocity.value
                    frameGyroSamples.add(GyroSample(gyroStartNs, 0f, initialVelocity, 0f))

                    // 2. Take picture via ImageCapture
                    imageCapture.targetRotation = targetRotation
                    val packet = captureSingleFrameInternal(
                        imageCapture = imageCapture,
                        sequenceIndex = i,
                        totalCount = actualCount,
                        gyroStartNs = gyroStartNs,
                        targetRotation = targetRotation,
                    )

                    val now = System.currentTimeMillis()
                    interFrameIntervals.add(now - lastFrameTime)
                    lastFrameTime = now

                    packets.add(packet)
                    onProgress?.invoke(i + 1, actualCount)
                }
            }

            // Restore baseline EV compensation if modified
            if (validEvOffsets.any { it != 0 } && cameraControl != null) {
                try {
                    cameraControl.setExposureCompensationIndex(0)
                } catch (_: Exception) {}
            }

            val totalDuration = System.currentTimeMillis() - burstStartTime
            val avgLatency = if (packets.isNotEmpty()) totalDuration.toFloat() / packets.size else 0f

            val diagnostics = BurstDiagnostics(
                burstPreparationTimeMs = 0L,
                interFrameIntervalsMs = interFrameIntervals,
                totalBurstDurationMs = totalDuration,
                averageFrameLatencyMs = avgLatency,
                droppedFramesCount = actualCount - packets.size,
                poolStats = bufferPool.getStats(),
            )

            logger.i(TAG, "Burst acquisition completed successfully: ${packets.size}/$actualCount frames in ${totalDuration}ms.")
            OptiResult.Success(
                BurstResult(
                    packets = packets,
                    modeUsed = mode,
                    diagnostics = diagnostics,
                    isFallbackSingleFrame = false,
                )
            )
        } catch (e: Exception) {
            logger.w(TAG, "Burst acquisition interrupted or failed: ${e.message}. Evaluating fallback...")

            // Clean up partially acquired packets if cancelled
            if (e is CancellationException) {
                packets.forEach { it.close() }
                return@withContext OptiResult.Error(OptiError.ProcessingFailed("Burst capture cancelled", e))
            }

            // Task 7: Graceful fallback to single capture
            val fallbackResult = executeSingleFrameFallback(
                imageCapture = imageCapture,
                mode = mode,
                targetRotation = targetRotation,
            )

            // If fallback succeeded, close any partial packets and return fallback result
            if (fallbackResult is OptiResult.Success) {
                packets.forEach { it.close() }
                return@withContext fallbackResult
            }

            // If fallback also failed, close partial packets and return error
            packets.forEach { it.close() }
            OptiResult.Error(OptiError.ProcessingFailed("Burst acquisition failed: ${e.message}", e))
        }
    }

    override fun cancel() {
        isCancelled.set(true)
    }

    private suspend fun captureSingleFrameInternal(
        imageCapture: ImageCapture,
        sequenceIndex: Int,
        totalCount: Int,
        gyroStartNs: Long,
        targetRotation: Int,
    ): FramePacket = suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val gyroEndNs = System.nanoTime()
                    try {
                        // 1. Extract Camera2 TotalCaptureResult metadata
                        val captureResult = extractCaptureResultSafely(image)
                        val exposureTimeNs = captureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                        val iso = captureResult?.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100
                        val focusDistance = captureResult?.get(CaptureResult.LENS_FOCUS_DISTANCE)
                        val lensState = captureResult?.get(CaptureResult.LENS_STATE)
                        val aeState = captureResult?.get(CaptureResult.CONTROL_AE_STATE)
                        val awbState = captureResult?.get(CaptureResult.CONTROL_AWB_STATE)
                        val aperture = captureResult?.get(CaptureResult.LENS_APERTURE)
                        val focalLength = captureResult?.get(CaptureResult.LENS_FOCAL_LENGTH)

                        val metadata = FrameMetadata(
                            timestampNs = gyroStartNs,
                            exposureTimeNs = exposureTimeNs,
                            iso = iso,
                            focusDistanceDiopters = focusDistance,
                            lensState = lensState,
                            aeState = aeState,
                            awbState = awbState,
                            aperture = aperture,
                            focalLengthMm = focalLength,
                            orientationDegrees = image.imageInfo.rotationDegrees,
                        )

                        // 2. Sample Gyro window
                        val currentGyroSpeed = gyroMotionTracker.angularVelocity.value
                        val gyroWindow = GyroWindow(
                            samples = listOf(
                                GyroSample(gyroStartNs, 0f, currentGyroSpeed, 0f),
                                GyroSample(gyroEndNs, 0f, currentGyroSpeed, 0f),
                            ),
                            startTimestampNs = gyroStartNs,
                            endTimestampNs = gyroEndNs,
                        )

                        // 3. Copy bytes into BoundedBufferPool to prevent HAL buffer exhaustion
                        val plane = image.planes[0]
                        val byteBuffer = plane.buffer
                        val remainingBytes = byteBuffer.remaining()
                        val pooledBuffer = bufferPool.acquire(remainingBytes)
                        byteBuffer.get(pooledBuffer.data, 0, remainingBytes)

                        val packet = FramePacket(
                            sequenceIndex = sequenceIndex,
                            totalSequenceCount = totalCount,
                            buffer = pooledBuffer,
                            metadata = metadata,
                            gyroWindow = gyroWindow,
                            width = image.width,
                            height = image.height,
                            format = image.format,
                        )

                        continuation.resume(packet)
                    } catch (e: Exception) {
                        continuation.resumeWith(Result.failure(e))
                    } finally {
                        // Strict invariant: immediately close ImageProxy to release HAL buffer
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWith(Result.failure(exception))
                }
            }
        )
    }

    private suspend fun executeSingleFrameFallback(
        imageCapture: ImageCapture,
        mode: CaptureStrategyMode,
        targetRotation: Int,
    ): OptiResult<BurstResult> {
        logger.i(TAG, "Executing single-frame capture fallback...")
        return try {
            val gyroStartNs = System.nanoTime()
            val singlePacket = captureSingleFrameInternal(
                imageCapture = imageCapture,
                sequenceIndex = 0,
                totalCount = 1,
                gyroStartNs = gyroStartNs,
                targetRotation = targetRotation,
            )

            OptiResult.Success(
                BurstResult(
                    packets = listOf(singlePacket),
                    modeUsed = mode,
                    diagnostics = BurstDiagnostics(
                        burstPreparationTimeMs = 0L,
                        interFrameIntervalsMs = listOf(0L),
                        totalBurstDurationMs = 0L,
                        averageFrameLatencyMs = 0f,
                        droppedFramesCount = 0,
                        poolStats = bufferPool.getStats(),
                    ),
                    isFallbackSingleFrame = true,
                )
            )
        } catch (e: Exception) {
            logger.e(TAG, "Single-frame fallback also failed: ${e.message}", e)
            OptiResult.Error(OptiError.ProcessingFailed("Single frame fallback failed", e))
        }
    }

    private fun extractCaptureResultSafely(image: ImageProxy): CaptureResult? {
        return try {
            val imageInfo = image.imageInfo
            val getCameraCaptureResultMethod = imageInfo.javaClass.methods.firstOrNull { it.name == "getCameraCaptureResult" }
            val cameraCaptureResult = getCameraCaptureResultMethod?.invoke(imageInfo)
            if (cameraCaptureResult != null) {
                val getCaptureResultMethod = cameraCaptureResult.javaClass.methods.firstOrNull { it.name == "getCaptureResult" }
                val result = getCaptureResultMethod?.invoke(cameraCaptureResult)
                if (result is CaptureResult) return result
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "Camera2BurstEngine"
    }
}
