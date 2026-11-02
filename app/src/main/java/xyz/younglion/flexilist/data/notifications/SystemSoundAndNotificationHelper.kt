package xyz.younglion.flexilist.data.notifications

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

object SystemSoundAndNotificationHelper {

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
        WorkoutNotificationManager.showOrUpdateWorkoutNotification(context, workoutName, progressText)
    }

    fun dismissWorkoutNotification(context: Context) {
        WorkoutNotificationManager.dismissWorkoutNotification(context)
    }
}
