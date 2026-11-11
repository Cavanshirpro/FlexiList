package xyz.younglion.flexilist.ui.workspace

import android.content.Context
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.ListItemEntity

/** Intentional, previewed starting content. Existing user notebooks are never reseeded. */
enum class NotebookStarter(val label: Int, val kind: NotebookKind?, val emoji: String, val entries: List<Int>) {
    BLANK(R.string.v11_blank, null, "📝", emptyList()),
    WEEKLY(R.string.v11_weekly_review, NotebookKind.TASK, "🗓", listOf(R.string.v11_seed_plan, R.string.v11_seed_review, R.string.v11_seed_next)),
    GROCERIES(R.string.v11_groceries, NotebookKind.CHECKLIST, "🛒", listOf(R.string.v11_seed_milk, R.string.v11_seed_bread, R.string.v11_seed_fruit)),
    ROUTINE(R.string.v11_daily_habits, NotebookKind.HABIT, "🌱", listOf(R.string.v11_seed_read, R.string.v11_seed_move, R.string.v11_seed_tomorrow)),
    TRAINING(R.string.v11_workout_starter, NotebookKind.WORKOUT, "🏋", listOf(R.string.v11_seed_squat, R.string.v11_seed_plank)),
    BOOKS(R.string.v11_reading_queue, NotebookKind.READING, "📚", emptyList());

    fun items(context: Context, notebookId: Long = 0): List<ListItemEntity> = entries.mapIndexed { index, title ->
        ListItemEntity(notebookId = notebookId, title = context.getString(title), sortOrder = index,
            description = if (this != TRAINING) "" else if (index == 0)
                "[EXERCISE_CFG]type=Reps|sets=2|reps=10|time=30|rest=60|weight=0|muscles=Bacak|group=|days="
            else "[EXERCISE_CFG]type=Timer|sets=2|reps=10|time=30|rest=60|weight=0|muscles=Karın|group=|days=")
    }
}
