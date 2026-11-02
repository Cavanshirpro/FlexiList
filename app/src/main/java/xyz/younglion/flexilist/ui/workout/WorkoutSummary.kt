package xyz.younglion.flexilist.ui.workout

import org.json.JSONArray
import org.json.JSONObject

data class WorkoutSummary(
    val totalSets: Int,
    val totalWeightKg: Double,
    val completedExerciseIds: List<Long>,
    val exerciseStatsJson: String
)

fun summarizeWorkout(queue: List<WorkoutStep>, completedIndices: Set<Int>): WorkoutSummary {
    val done = completedIndices.mapNotNull { queue.getOrNull(it) }
    val stats = JSONArray()
    done.groupBy { it.exercise.id }.forEach { (_, steps) ->
        val exercise = steps.first().exercise
        stats.put(JSONObject().apply {
            put("exercise", exercise.name)
            put("sets", steps.size)
            put("weight", exercise.weightKg)
        })
    }
    return WorkoutSummary(
        totalSets = done.size,
        totalWeightKg = done.sumOf { it.exercise.weightKg * if (it.exercise.type == "Reps") it.exercise.targetReps else 1 },
        completedExerciseIds = queue.withIndex().groupBy { it.value.exercise.id }
            .filterValues { steps -> steps.all { it.index in completedIndices } }.keys.toList(),
        exerciseStatsJson = stats.toString()
    )
}
