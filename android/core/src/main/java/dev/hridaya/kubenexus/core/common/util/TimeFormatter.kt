package dev.hridaya.kubenexus.core.common.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object TimeFormatter {
    fun formatLastRefreshed(
        timestamp: Long?,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (timestamp == null || timestamp == 0L) return "Never refreshed"
        val diffMs = now - timestamp
        val instant = Instant.ofEpochMilli(timestamp)
        return when {
            diffMs < 5_000 -> "Refreshed just now"
            diffMs < 60_000 -> "Refreshed ${diffMs / 1000}s ago"
            diffMs < 3600_000 -> "Refreshed ${diffMs / 60000}m ago"
            instant.atZone(zone).toLocalDate() == Instant.ofEpochMilli(now).atZone(zone)
                .toLocalDate() ->
                "Refreshed at ${
                    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()).withZone(zone)
                        .format(instant)
                }"
            // Cached data can be days old; a bare time of day would read as today.
            else -> "Refreshed on ${
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withZone(zone).format(instant)
            }"
        }
    }

    fun formatIsoToLocal(isoTimestamp: String?): String {
        if (isoTimestamp.isNullOrBlank()) return "N/A"
        return try {
            val instant = Instant.parse(isoTimestamp)
            val formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            try {
                val zonedDateTime = ZonedDateTime.parse(isoTimestamp)
                val formatter = DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault())
                formatter.format(zonedDateTime)
            } catch (_: Exception) {
                isoTimestamp
            }
        }
    }
}
