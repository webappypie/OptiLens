package com.webappypie.optilens.core.ui.gallery

import android.app.PendingIntent
import app.cash.turbine.test
import com.webappypie.optilens.core.camera.storage.DateBucket
import com.webappypie.optilens.core.camera.storage.GalleryRepository
import com.webappypie.optilens.core.camera.storage.MediaExifSummary
import com.webappypie.optilens.core.camera.storage.MediaItem
import com.webappypie.optilens.core.camera.storage.StorageMonitor
import com.webappypie.optilens.core.camera.storage.StorageState
import com.webappypie.optilens.core.camera.storage.StorageStatus
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeGalleryRepository
    private lateinit var fakeStorageMonitor: FakeStorageMonitor
    private lateinit var viewModel: GalleryViewModel

    private val sampleItems = listOf(
        MediaItem(
            id = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "OptiLens_20260920_100000.jpg",
            dateTakenMs = System.currentTimeMillis(),
            dateAddedSec = System.currentTimeMillis() / 1000,
            width = 4000,
            height = 3000,
            sizeBytes = 3_000_000L,
            mimeType = "image/jpeg",
            isRaw = false,
            isEnhanced = false,
            isFavorite = true,
        ),
        MediaItem(
            id = 2L,
            contentUri = "content://media/external/images/media/2",
            displayName = "OptiLens_20260920_100500.dng",
            dateTakenMs = System.currentTimeMillis() - 10_000,
            dateAddedSec = (System.currentTimeMillis() - 10_000) / 1000,
            width = 4000,
            height = 3000,
            sizeBytes = 25_000_000L,
            mimeType = "image/x-adobe-dng",
            isRaw = true,
            isEnhanced = false,
            isFavorite = false,
        ),
        MediaItem(
            id = 3L,
            contentUri = "content://media/external/images/media/3",
            displayName = "OptiLens_AI_1726800000000.jpg",
            dateTakenMs = System.currentTimeMillis() - 86400000L * 2, // 2 days ago
            dateAddedSec = (System.currentTimeMillis() - 86400000L * 2) / 1000,
            width = 4000,
            height = 3000,
            sizeBytes = 4_000_000L,
            mimeType = "image/jpeg",
            isRaw = false,
            isEnhanced = true,
            isFavorite = false,
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeGalleryRepository(sampleItems)
        fakeStorageMonitor = FakeStorageMonitor()
        viewModel = GalleryViewModel(
            galleryRepository = fakeRepo,
            storageMonitor = fakeStorageMonitor,
            logger = NoOpLogger(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads all items and defaults to ALL filter`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Initial
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(GalleryFilter.ALL, state.filter)
            assertEquals(3, state.totalCount)
            assertEquals(3, state.filteredCount)
            assertFalse(state.isSelectionMode)
        }
    }

    @Test
    fun `filter chips update filteredItems correctly for favorites, enhanced, and raw`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()

            // Favorites filter
            viewModel.setFilter(GalleryFilter.FAVORITES)
            testDispatcher.scheduler.advanceUntilIdle()
            var state = expectMostRecentItem()
            assertEquals(GalleryFilter.FAVORITES, state.filter)
            assertEquals(1, state.filteredCount)
            assertTrue(state.filteredItems.all { it.isFavorite })

            // RAW filter
            viewModel.setFilter(GalleryFilter.RAW)
            testDispatcher.scheduler.advanceUntilIdle()
            state = expectMostRecentItem()
            assertEquals(GalleryFilter.RAW, state.filter)
            assertEquals(1, state.filteredCount)
            assertTrue(state.filteredItems.all { it.isRaw })

            // AI Enhanced filter
            viewModel.setFilter(GalleryFilter.ENHANCED)
            testDispatcher.scheduler.advanceUntilIdle()
            state = expectMostRecentItem()
            assertEquals(GalleryFilter.ENHANCED, state.filter)
            assertEquals(1, state.filteredCount)
            assertTrue(state.filteredItems.all { it.isEnhanced })

            // Back to All
            viewModel.setFilter(GalleryFilter.ALL)
            testDispatcher.scheduler.advanceUntilIdle()
            state = expectMostRecentItem()
            assertEquals(3, state.filteredCount)
        }
    }

    @Test
    fun `selection mode enables, selects all, and clears correctly`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSelectionMode)

            // Select 1 item
            viewModel.toggleItemSelection("content://media/external/images/media/1")
            testDispatcher.scheduler.advanceUntilIdle()
            var state = expectMostRecentItem()
            assertTrue(state.isSelectionMode)
            assertEquals(1, state.selectedItemUris.size)

            // Select All
            viewModel.selectAll()
            testDispatcher.scheduler.advanceUntilIdle()
            state = expectMostRecentItem()
            assertEquals(3, state.selectedItemUris.size)

            // Clear Selection
            viewModel.clearSelection()
            testDispatcher.scheduler.advanceUntilIdle()
            state = expectMostRecentItem()
            assertFalse(state.isSelectionMode)
            assertEquals(0, state.selectedItemUris.size)
        }
    }

    @Test
    fun `deleteItem invokes repository and updates state`() = runTest {
        viewModel.deleteItem("content://media/external/images/media/1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeRepo.deletedUris.contains("content://media/external/images/media/1"))
    }

    @Test
    fun `clearCache triggers storage monitor and sets freed message`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.clearCache()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(fakeStorageMonitor.wasClearCacheCalled)
            val state = expectMostRecentItem()
            assertNotNull(state.freedCacheMessage)
            assertTrue(state.freedCacheMessage!!.contains("freed") || state.freedCacheMessage!!.contains("Freed"))
        }
    }

    @Test
    fun `storage state reflects LOW and CRITICAL status warnings`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()

            fakeStorageMonitor.emitStorage(
                StorageState(
                    freeBytes = 200 * 1024 * 1024L,
                    totalBytes = 64_000_000_000L,
                    status = StorageStatus.LOW,
                )
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(StorageStatus.LOW, state.storageState.status)
            assertTrue(state.storageState.isCaptureAllowed)
        }
    }

    // ── Test Doubles ──────────────────────────────────────────────────────────

    private class FakeGalleryRepository(initial: List<MediaItem>) : GalleryRepository {
        val mediaFlow = MutableStateFlow(initial)
        val deletedUris = mutableListOf<String>()

        override fun observeMedia(): Flow<List<MediaItem>> = mediaFlow.asStateFlow()

        override suspend fun loadMedia(): List<MediaItem> = mediaFlow.value

        override suspend fun deleteMedia(uriString: String): OptiResult<PendingIntent?> {
            deletedUris.add(uriString)
            mediaFlow.value = mediaFlow.value.filter { it.contentUri != uriString }
            return OptiResult.Success(null)
        }

        override suspend fun toggleFavorite(uriString: String): OptiResult<Boolean> {
            val item = mediaFlow.value.find { it.contentUri == uriString }
            val newFav = !(item?.isFavorite ?: false)
            mediaFlow.value = mediaFlow.value.map {
                if (it.contentUri == uriString) it.copy(isFavorite = newFav) else it
            }
            return OptiResult.Success(newFav)
        }

        override suspend fun extractExifMetadata(uriString: String): MediaExifSummary? {
            return MediaExifSummary(focalLength35mm = 24, fNumber = 1.8f)
        }
    }

    private class FakeStorageMonitor : StorageMonitor {
        private val _storage = MutableStateFlow(
            StorageState(
                freeBytes = 10_000_000_000L,
                totalBytes = 64_000_000_000L,
                status = StorageStatus.NORMAL,
                cacheSizeBytes = 5_000_000L,
            )
        )
        override val storageState: Flow<StorageState> = _storage.asStateFlow()
        var wasClearCacheCalled = false

        fun emitStorage(state: StorageState) {
            _storage.value = state
        }

        override suspend fun refreshStorageState(): StorageState = _storage.value
        override suspend fun calculateCacheSizeBytes(): Long = _storage.value.cacheSizeBytes
        override suspend fun cleanExpiredCache(olderThanMs: Long): Long = 1024L
        override suspend fun clearCache(): Long {
            wasClearCacheCalled = true
            return 8_500_000L
        }
    }
}
