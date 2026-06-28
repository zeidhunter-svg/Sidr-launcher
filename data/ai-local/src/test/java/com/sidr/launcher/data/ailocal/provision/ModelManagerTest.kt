package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.core.testing.FakeDeviceProfileProvider
import com.sidr.launcher.core.testing.FakeModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.device.DeviceProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerTest {

    private val modelId = ModelId("intent-nlu-v1")
    private val pinned = ModelDownloadConfig(modelId, "https://example.test/model.onnx", "deadbeef")

    private fun manager(
        profile: DeviceProfile,
        availability: ModelAvailability,
        scheduler: FakeModelDownloadScheduler,
        config: ModelDownloadConfig = pinned,
    ): ModelManager {
        val avail = FakeModelAvailabilityRepository().apply { setAvailability(modelId, availability) }
        return ModelManager(
            deviceProfileProvider = FakeDeviceProfileProvider(initialProfile = profile),
            availability = avail,
            scheduler = scheduler,
            config = config,
        )
    }

    @Test
    fun `LOW_END never schedules the download`() = runTest {
        val scheduler = FakeModelDownloadScheduler()
        manager(DeviceProfile.LOW_END, ModelAvailability.Missing, scheduler).ensureModel()
        assertEquals(emptyList<ModelId>(), scheduler.scheduled)
    }

    @Test
    fun `MID_RANGE not-Available schedules exactly one download`() = runTest {
        val scheduler = FakeModelDownloadScheduler()
        manager(DeviceProfile.MID_RANGE, ModelAvailability.Missing, scheduler).ensureModel()
        assertEquals(listOf(modelId), scheduler.scheduled)
    }

    @Test
    fun `already-Available is idempotent - schedules nothing`() = runTest {
        val scheduler = FakeModelDownloadScheduler()
        manager(DeviceProfile.HIGH_END, ModelAvailability.Available, scheduler).ensureModel()
        assertEquals(emptyList<ModelId>(), scheduler.scheduled)
    }

    @Test
    fun `unpinned config (OQ#2 open) schedules nothing even on a capable device`() = runTest {
        val scheduler = FakeModelDownloadScheduler()
        manager(DeviceProfile.HIGH_END, ModelAvailability.Missing, scheduler, ModelDownloadConfig.INTENT_NLU_PENDING).ensureModel()
        assertEquals(emptyList<ModelId>(), scheduler.scheduled)
    }
}
