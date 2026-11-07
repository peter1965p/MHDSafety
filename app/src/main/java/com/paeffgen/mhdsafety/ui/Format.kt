package com.paeffgen.mhdsafety.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.paeffgen.mhdsafety.domain.ExpiryStatus
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun LocalDate.formatDe(): String = format(dateFormatter)

/** DatePicker arbeitet mit UTC-Millisekunden. */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun utcMillisToLocalDate(millis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

fun daysText(days: Long): String = when {
    days > 1 -> "noch $days Tage"
    days == 1L -> "morgen"
    days == 0L -> "heute"
    days == -1L -> "seit gestern"
    else -> "seit ${abs(days)} Tagen"
}

/** Ampelfarben – im Dunkeln heller, damit sie auf dunklem Grund gut lesbar sind. */
@Composable
fun ExpiryStatus.color(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (this) {
        ExpiryStatus.OK -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        ExpiryStatus.SOON -> if (dark) Color(0xFFFFD54F) else Color(0xFFF9A825)
        ExpiryStatus.DUE -> if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
        ExpiryStatus.EXPIRED -> if (dark) Color(0xFFE57373) else Color(0xFFC62828)
    }
}
