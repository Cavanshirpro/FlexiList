package xyz.younglion.flexilist.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import xyz.younglion.flexilist.MainActivity

object WorkoutNotificationManager {
    private const val CHANNEL_ID = "flexilist_workout_ongoing"
    private const val NOTIFICATION_ID = 9999

    fun showOrUpdateWorkoutNotification(
        context: Context,
        workoutName: String,
        statusText: String,
        isPaused: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FlexiList Canlı Antrenman Takibi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktif antrenman sürecinde gösterilen canlı ve kalıcı durum bildirimi."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openWorkoutPlayer", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) "⏸️ FlexiList Antrenman (Duraklatıldı): $workoutName" else "⚡ FlexiList Antrenman: $workoutName"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Kalıcı, silinemeyen bildirim
            .setOnlyAlertOnce(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun dismissWorkoutNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
