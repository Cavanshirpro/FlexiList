package xyz.younglion.flexilist

import org.junit.Assert.*
import org.junit.Test
import xyz.younglion.flexilist.ui.detail.parseExerciseConfig
import xyz.younglion.flexilist.data.model.NotebookEntity

class ExerciseConfigTest {
    @Test fun invalidExerciseParametersCannotCreateEmptyQueuesOrNonfiniteLogs() {
        val config = parseExerciseConfig("[EXERCISE_CFG]type=unknown|sets=-4|reps=0|time=0|rest=-1|weight=NaN|group=A=B")
        assertEquals("Reps", config.type)
        assertEquals(1, config.setsCount)
        assertEquals(1, config.targetReps)
        assertEquals(1, config.targetTimeSeconds)
        assertEquals(0, config.restTimeSeconds)
        assertEquals(0.0, config.weightKg, 0.0)
        assertEquals("A=B", config.supersetGroupId)
    }

    @Test fun zeroRestIsValidAndCyclicProgramsCannotDivideByZero() {
        assertEquals(0, parseExerciseConfig("[EXERCISE_CFG]rest=0").restTimeSeconds)
        val config = NotebookEntity.parseWorkoutScheduleConfig("[WORKOUT_CFG]frequency=Daily|workoutDays=0|restDays=-2")
        assertTrue(config.workoutDurationDays + config.restDurationDays > 0)
    }
}
