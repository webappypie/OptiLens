package com.webappypie.optilens.core.camera.analyzer

import androidx.camera.core.ImageProxy
import com.webappypie.optilens.core.camera.analysis.face.FaceDetector
import com.webappypie.optilens.core.camera.model.DetectedFace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Regression for the native crash seen on device (SIGSEGV in libmedia_jni Image_getFormat via
 * ML Kit InputImage.fromMediaImage): the analyzer closed each frame while asynchronous face
 * detection was still reading it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeIntelligenceAnalyzerImageLifecycleTest {

    private val dispatcher = StandardTestDispatcher()

    /** Minimal ImageProxy (no planes) that records how often it is closed. */
    private class CountingFrame {
        var closeCount = 0
        val proxy: ImageProxy = Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java),
        ) { self, method, args ->
            when (method.name) {
                "close" -> { closeCount++; null }
                "getPlanes" -> emptyArray<ImageProxy.PlaneProxy>()
                "getWidth" -> 640
                "getHeight" -> 480
                "getFormat" -> 35
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                "toString" -> "CountingFrame"
                else -> null
            }
        } as ImageProxy
    }

    /** Face detector that holds the frame until [release] is completed, like an ML Kit task. */
    private class SuspendingFaceDetector : FaceDetector {
        val release = CompletableDeferred<Unit>()
        var observedCloseCountDuringDetection = -1
        var frame: CountingFrame? = null

        override suspend fun detectFaces(imageProxy: ImageProxy): List<DetectedFace> {
            release.await()
            observedCloseCountDuringDetection = frame?.closeCount ?: -1
            return emptyList()
        }

        override fun release() = Unit
    }

    private fun analyzer(detector: FaceDetector, scope: CoroutineScope) =
        RealtimeIntelligenceAnalyzer(faceDetector = detector, analysisScope = scope)

    @Test
    fun `frame handed to face detection stays open until detection finishes`() {
        val detector = SuspendingFaceDetector()
        val frame = CountingFrame().also { detector.frame = it }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        analyzer(detector, scope).analyze(frame.proxy)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Frame must not be closed while ML Kit may still read it", 0, frame.closeCount)

        detector.release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, detector.observedCloseCountDuringDetection)
        assertEquals("Frame is closed exactly once after detection", 1, frame.closeCount)
    }

    @Test
    fun `throttled frames that skip detection are closed immediately`() {
        val detector = SuspendingFaceDetector().also { it.release.complete(Unit) }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val analyzer = analyzer(detector, scope)

        val first = CountingFrame()
        analyzer.analyze(first.proxy)
        // Arrives within the 66 ms fast-loop interval, so it is dropped without analysis.
        val second = CountingFrame()
        analyzer.analyze(second.proxy)
        assertEquals(1, second.closeCount)

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, first.closeCount)
    }

    @Test
    fun `frame is still closed exactly once when the analysis scope was cancelled`() {
        val detector = SuspendingFaceDetector().also { it.release.complete(Unit) }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        scope.cancel()

        val frame = CountingFrame()
        analyzer(detector, scope).analyze(frame.proxy)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, frame.closeCount)
    }
}
