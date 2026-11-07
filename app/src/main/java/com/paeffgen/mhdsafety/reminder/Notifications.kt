package com.paeffgen.mhdsafety.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.paeffgen.mhdsafety.MainActivity
import com.paeffgen.mhdsafety.R
import com.paeffgen.mhdsafety.domain.DailyReminder

object Notifications {
    private const val CHANNEL_ID = "expiry_alarm"
    private const val DAILY_ID = 1
    private const val UPDATE_CHANNEL_ID = "app_update"
    private const val UPDATE_ID = 2
    private const val MAX_LINES = 6

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ablauf-Alarm",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Tägliche Erinnerung an Lebensmittel, die ablaufen" }
        val updateChannel = NotificationChannel(
            UPDATE_CHANNEL_ID,
            "App-Updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Hinweis, wenn eine neue Version von MHD Safety bereitsteht" }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(channel, updateChannel))
    }

    fun canNotify(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showDaily(context: Context, reminder: DailyReminder) {
        val lines = reminder.lines()
        val style = NotificationCompat.InboxStyle().setBigContentTitle(reminder.title())
        lines.take(MAX_LINES).forEach(style::addLine)
        if (lines.size > MAX_LINES) style.setSummaryText("+ ${lines.size - MAX_LINES} weitere")

        show(
            context,
            title = reminder.title(),
            text = lines.joinToString("  ·  "),
            style = style,
        )
    }

    fun showTest(context: Context) {
        show(
            context,
            title = "Test: MHD Safety",
            text = "So sieht der tägliche Alarm aus.",
            style = null,
        )
    }

    fun showUpdate(context: Context, version: String) {
        show(
            context,
            title = "Update verfügbar",
            text = "MHD Safety $version ist da. Antippen zum Installieren.",
            style = null,
            channelId = UPDATE_CHANNEL_ID,
            notificationId = UPDATE_ID,
        )
    }

    private fun show(
        context: Context,
        title: String,
        text: String,
        style: NotificationCompat.Style?,
        channelId: String = CHANNEL_ID,
        notificationId: Int = DAILY_ID,
    ) {
        if (!canNotify(context)) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Berechtigung wurde zwischen Prüfung und Anzeige entzogen.
        }
    }
}
