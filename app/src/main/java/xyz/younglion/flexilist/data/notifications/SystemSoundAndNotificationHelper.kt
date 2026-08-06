package xyz.younglion.flexilist.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.NotificationCompat
import xyz.younglion.flexilist.MainActivity

object SystemSoundAndNotificationHelper {

    private const val WORKOUT_NOTIFICATION_ID = 999
    private const val CHANNEL_ID = "workout_session_channel"

    fun playTick() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            toneG.startTone(ToneGenerator.TONE_CDMA_PIP, 80)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playDing() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showWorkoutNotification(context: Context, workoutName: String, progressText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aktif Antrenman Takibi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Antrenman esnasında sürenizi ve aktif seti gösterir."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            WORKOUT_NOTIFICATION_ID,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FlexiList: Aktif Antrenman")
            .setContentText("$workoutName - $progressText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        notificationManager.notify(WORKOUT_NOTIFICATION_ID, builder.build())
    }

    fun dismissWorkoutNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(WORKOUT_NOTIFICATION_ID)
    }
}
