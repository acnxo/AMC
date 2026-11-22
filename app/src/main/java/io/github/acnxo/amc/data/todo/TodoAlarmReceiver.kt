package io.github.acnxo.amc.data.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收待办闹钟，弹出通知。 */
class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TodoReminder.showNotification(
            context = context,
            title = TodoReminder.readTitle(intent),
            content = TodoReminder.readContent(intent),
        )
    }
}
