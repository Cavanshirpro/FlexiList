package xyz.younglion.flexilist

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.younglion.flexilist.ui.workout.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkoutSummaryTest {
    private fun exercise(id: Long, name: String = "Bench \"A\"\nPress") = PlayableExercise(
        id, name, "Reps", 2, 10, 30, 0, 20.0, emptyList(), "")
    private val first = exercise(1)
    private val second = exercise(2)
    private val queue = listOf(WorkoutStep(first, 1, false), WorkoutStep(first, 2, false), WorkoutStep(second, 1, false))

    @Test fun earlyFinishDoesNotCompleteUnfinishedExercises() {
        val summary = summarizeWorkout(queue, setOf(0))
        assertEquals(1, summary.totalSets)
        assertTrue(summary.completedExerciseIds.isEmpty())
        assertEquals(200.0, summary.totalWeightKg, 0.0)
    }

    @Test fun identicalNamesStaySeparateAndJsonIsEscaped() {
        val summary = summarizeWorkout(queue, setOf(0, 1, 2))
        assertEquals(listOf(1L, 2L), summary.completedExerciseIds)
        assertEquals(600.0, summary.totalWeightKg, 0.0)
        val stats = JSONArray(summary.exerciseStatsJson)
        assertEquals(2, stats.length())
        assertEquals(first.name, stats.getJSONObject(0).getString("exercise"))
    }

    @Test fun finishingBeforeAnySetCompletesNothing() {
        val summary = summarizeWorkout(queue, emptySet())
        assertEquals(0, summary.totalSets)
        assertEquals(0.0, summary.totalWeightKg, 0.0)
        assertTrue(summary.completedExerciseIds.isEmpty())
    }
}
