package io.github.acnxo.amc.data.todo

import android.Manifest
import android.app.AlarmManager
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
import io.github.acnxo.amc.MainActivity
import io.github.acnxo.amc.R

/** 待办的本地闹钟提醒：注册/取消精确闹钟，并在触发时发通知。 */
object TodoReminder {

    const val CHANNEL_ID = "todo_reminders"

    private const val EXTRA_TASK_NO = "task_no"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_CONTENT = "content"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "待办提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "待办事项到点提醒"
            },
        )
    }

    fun canPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }

    fun schedule(
        context: Context,
        taskNo: Long,
        title: String,
        content: String?,
        triggerAtMillis: Long,
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        ensureChannel(context)
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_NO, taskNo)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CONTENT, content)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            taskNo.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        if (canScheduleExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancel(context: Context, taskNo: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            taskNo.toInt(),
            Intent(context, TodoAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(pending)
    }

    fun showNotification(context: Context, title: String, content: String?) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "待办提醒" })
            .setContentText(content.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.orEmpty()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
    }

    fun readTaskNo(intent: Intent): Long = intent.getLongExtra(EXTRA_TASK_NO, 0L)
    fun readTitle(intent: Intent): String = intent.getStringExtra(EXTRA_TITLE).orEmpty()
    fun readContent(intent: Intent): String? = intent.getStringExtra(EXTRA_CONTENT)
}
