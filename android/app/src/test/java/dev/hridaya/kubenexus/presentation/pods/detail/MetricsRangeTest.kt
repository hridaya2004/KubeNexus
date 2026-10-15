package dev.hridaya.kubenexus.presentation.pods.detail

import dev.hridaya.kubenexus.domain.model.PodMetricSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsRangeTest {

    // Samples every 15 s, as metrics-server produces them, timestamped in the cluster's clock.
    private val clusterNow = 1_000_000_000L
    private val samples = (0..20).map { i ->
        PodMetricSample(
            podName = "web",
            timestampMillis = clusterNow - i * 15_000L,
            cpuCores = 0.1,
            memoryBytes = 1L
        )
    }.shuffled()

    private fun ages(window: List<PodMetricSample>) =
        window.map { (clusterNow - it.timestampMillis) / 1000 }

    @Test
    fun `each range keeps the samples within it of the newest, oldest first`() {
        assertEquals(listOf(30L, 15L, 0L), ages(MetricsRange.SECONDS_30.window(samples)))
        assertEquals(listOf(60L, 45L, 30L, 15L, 0L), ages(MetricsRange.MINUTES_1.window(samples)))
        assertEquals(
            (0L..300L step 15).reversed().toList(),
            ages(MetricsRange.MINUTES_5.window(samples))
        )
    }

    // The window is measured from the newest sample, so it works whatever the device clock says.
    @Test
    fun `the window does not depend on the device clock`() {
        val fromLastYear =
            samples.map { it.copy(timestampMillis = it.timestampMillis - 365L * 24 * 3600 * 1000) }

        assertEquals(3, MetricsRange.SECONDS_30.window(fromLastYear).size)
    }

    @Test
    fun `a range shorter than the metrics resolution still shows the latest change`() {
        val everyMinute = listOf(0L, 60_000L, 120_000L).map {
            PodMetricSample(podName = "web", timestampMillis = it, cpuCores = 0.1, memoryBytes = 1L)
        }

        assertEquals(
            listOf(60_000L, 120_000L),
            MetricsRange.SECONDS_30.window(everyMinute).map { it.timestampMillis })
        assertEquals(emptyList<PodMetricSample>(), MetricsRange.SECONDS_30.window(emptyList()))
        assertEquals(1, MetricsRange.SECONDS_30.window(everyMinute.take(1)).size)
    }

    @Test
    fun `every range spans at least two metrics-server samples`() {
        MetricsRange.entries.forEach { range ->
            assertTrue(
                "${range.label} is shorter than two 15 s samples",
                range.durationMs >= 30_000L
            )
        }
    }
}
