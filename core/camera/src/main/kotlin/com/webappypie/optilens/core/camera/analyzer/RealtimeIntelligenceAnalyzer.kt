package com.webappypie.optilens.core.camera.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.webappypie.optilens.core.camera.analysis.QualityMetricsEvaluator
import com.webappypie.optilens.core.camera.analysis.SceneClassifier
import com.webappypie.optilens.core.camera.analysis.SceneStabilizer
import com.webappypie.optilens.core.camera.analysis.YuvPreprocessor
import com.webappypie.optilens.core.camera.analysis.face.FaceDetector
import com.webappypie.optilens.core.camera.analysis.motion.SubjectMotionEstimator
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.HistogramData
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.strategy.AnalysisDiagnostics
import com.webappypie.optilens.core.camera.strategy.CaptureStrategy
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Unified high-performance [ImageAnalysis.Analyzer] running the full real-time intelligence loop:
 *
 * 1. Fast Sub-Sample Loop (~15 fps):
 *    - 64-bin Luminance Histogram
 *    - Radiometric & Optical Quality Metrics (mean luminance, clipping, Laplacian focus score, backlight)
 *    - Inter-frame Subject Motion vs. Gyro Camera Shake
 *
 * 2. Throttled AI Inference Loop (~4 fps / 250ms):
 *    - On-device Face & Landmark Detection
 *    - Multi-class Scene Classification (9 categories)
 *    - Temporal Scene Stabilization with Hysteresis
 *    - Capture Strategy Recommendation & Viewfinder Hints
 *
 * Guarantees strict deterministic [ImageProxy.close] on every invocation without blocking the camera preview.
 */
class RealtimeIntelligenceAnalyzer(
    private val yuvPreprocessor: YuvPreprocessor = YuvPreprocessor(),
    private val metricsEvaluator: QualityMetricsEvaluator = QualityMetricsEvaluator(),
    private val sceneClassifier: SceneClassifier = SceneClassifier(),
    private val sceneStabilizer: SceneStabilizer = SceneStabilizer(),
    private val faceDetector: FaceDetector,
    private val subjectMotionEstimator: SubjectMotionEstimator = SubjectMotionEstimator(),
    private val strategyEngine: CaptureStrategyEngine = CaptureStrategyEngine(),
    private val gyroVelocityProvider: () -> Float = { 0.0f },
    private val analysisScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onHistogramComputed: (HistogramData) -> Unit = {},
    private val onQualityMetricsComputed: (QualityMetrics) -> Unit = {},
    private val onMotionStateComputed: (MotionState) -> Unit = {},
    private val onSceneClassificationComputed: (SceneClassification) -> Unit = {},
    private val onFacesDetected: (List<DetectedFace>) -> Unit = {},
    private val onStrategyDecided: (CaptureStrategy) -> Unit = {},
) : ImageAnalysis.Analyzer {

    @Volatile
    var isEnabled: Boolean = true

    private var lastFastAnalysisTimestampMs = 0L
    private var lastAiInferenceTimestampMs = 0L

    private val fastLoopIntervalMs = 66L // ~15 fps
    private val aiInferenceIntervalMs = 250L // ~4 fps

    private var smoothedFps = 15.0f
    private var lastFrameDurationMs = 66L

    private var cachedFaces: List<DetectedFace> = emptyList()
    private var cachedScene: SceneClassification = SceneClassification.DEFAULT

    override fun analyze(image: ImageProxy) {
        val startTimeNs = System.nanoTime()
        val now = System.currentTimeMillis()

        try {
            if (!isEnabled || (now - lastFastAnalysisTimestampMs) < fastLoopIntervalMs) {
                return
            }

            val elapsedMs = (now - lastFastAnalysisTimestampMs).coerceAtLeast(1L)
            lastFastAnalysisTimestampMs = now
            val currentInstantFps = 1000.0f / elapsedMs.toFloat()
            smoothedFps = (0.2f * currentInstantFps) + (0.8f * smoothedFps)

            // 1. Fast Preprocessing (YUV subsample, luminance grid, chrominance averages)
            val frameData = yuvPreprocessor.process(image)

            // 2. Optical Quality Metrics (Focus sharpness, clipping, backlight)
            val qualityMetrics = metricsEvaluator.evaluate(frameData)
            onQualityMetricsComputed(qualityMetrics)

            // 3. 64-bin Luminance Histogram
            val histogram = HistogramData(
                bins = frameData.histogramBins,
                maxCount = 1.0f,
            )
            onHistogramComputed(histogram)

            // 4. Motion Estimation (Subject motion isolated from gyro camera shake)
            val currentGyro = gyroVelocityProvider()
            val motionState = subjectMotionEstimator.estimateMotion(
                currentGrid = frameData.yGrid,
                gyroAngularVelocity = currentGyro,
                timestampMs = now,
            )
            onMotionStateComputed(motionState)

            // 5. Throttled AI Inference (~4 fps)
            val shouldRunAi = (now - lastAiInferenceTimestampMs) >= aiInferenceIntervalMs
            if (shouldRunAi) {
                lastAiInferenceTimestampMs = now

                // Execute Face Detection and Scene Classification
                analysisScope.launch {
                    val aiStartTimeNs = System.nanoTime()
                    try {
                        val detectedFaces = faceDetector.detectFaces(image)
                        cachedFaces = detectedFaces
                        onFacesDetected(detectedFaces)

                        val rawScene = sceneClassifier.classify(
                            frameData = frameData,
                            metrics = qualityMetrics,
                            motionState = motionState,
                            faces = detectedFaces,
                        )

                        val stabilizedScene = sceneStabilizer.stabilize(rawScene)
                        cachedScene = stabilizedScene
                        onSceneClassificationComputed(stabilizedScene)

                        val aiDurationMs = (System.nanoTime() - aiStartTimeNs) / 1_000_000L

                        val diagnostics = AnalysisDiagnostics(
                            analysisFps = smoothedFps,
                            inferenceLatencyMs = aiDurationMs,
                            frameProcessingTimeMs = lastFrameDurationMs,
                        )

                        val strategy = strategyEngine.decideStrategy(
                            scene = stabilizedScene,
                            quality = qualityMetrics,
                            motion = motionState,
                            supportsHdr = true,
                            diagnostics = diagnostics,
                        )
                        onStrategyDecided(strategy)
                    } catch (_: Exception) {
                        // Drop frame gracefully on lifecycle transitions
                    }
                }
            } else {
                // Update strategy between AI inferences with latest fast quality and motion
                val diagnostics = AnalysisDiagnostics(
                    analysisFps = smoothedFps,
                    inferenceLatencyMs = 0L,
                    frameProcessingTimeMs = lastFrameDurationMs,
                )
                val strategy = strategyEngine.decideStrategy(
                    scene = cachedScene,
                    quality = qualityMetrics,
                    motion = motionState,
                    supportsHdr = true,
                    diagnostics = diagnostics,
                )
                onStrategyDecided(strategy)
            }

            lastFrameDurationMs = (System.nanoTime() - startTimeNs) / 1_000_000L
        } catch (_: Exception) {
            // Drop gracefully on format / orientation / lifecycle transition
        } finally {
            // Mandatory invariant: strictly guarantee image closure
            image.close()
        }
    }

    fun reset() {
        sceneStabilizer.reset()
        subjectMotionEstimator.reset()
        cachedFaces = emptyList()
        cachedScene = SceneClassification.DEFAULT
    }
}
