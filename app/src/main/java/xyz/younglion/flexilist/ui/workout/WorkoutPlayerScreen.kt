package xyz.younglion.flexilist.ui.workout

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.notifications.SystemSoundAndNotificationHelper
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.detail.parseExerciseConfig
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import xyz.younglion.flexilist.ui.notebooks.varRemember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkoutPlayerScreen(
    viewModel: FlexiViewModel,
    notebookId: Long = 0L,
    workoutName: String,
    exercises: List<ListItemEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Flatten exercise items into a structured sequence of exercises and sets
    // If we have Supersets, we can interleave them based on their supersetGroupId
    val exerciseConfigs = remember(exercises) {
        exercises.map { item ->
            val cfg = parseExerciseConfig(item.description)
            PlayableExercise(
                id = item.id,
                name = item.title,
                type = cfg.type, // "Reps" or "Timer"
                setsCount = cfg.setsCount,
                targetReps = cfg.targetReps,
                targetTimeSeconds = cfg.targetTimeSeconds,
                restTimeSeconds = cfg.restTimeSeconds,
                weightKg = cfg.weightKg,
                targetMuscles = cfg.targetMuscles,
                supersetGroupId = cfg.supersetGroupId
            )
        }
    }

    // Generate execution queue of set-by-set steps
    // Standard: Exercise 1 Set 1, Set 2, Set 3 -> Exercise 2
    // Superset: If consecutive exercises share same non-empty group, interleave their sets!
    val executionQueue = remember(exerciseConfigs) {
        val queue = mutableStateListOf<WorkoutStep>()
        var i = 0
        while (i < exerciseConfigs.size) {
            val current = exerciseConfigs[i]
            val group = current.supersetGroupId

            if (group.isNotBlank()) {
                // Find contiguous exercises with same supersetGroupId
                val groupExercises = mutableListOf(current)
                var j = i + 1
                while (j < exerciseConfigs.size && exerciseConfigs[j].supersetGroupId == group) {
                    groupExercises.add(exerciseConfigs[j])
                    j++
                }

                val maxSets = groupExercises.maxOf { it.setsCount }
                // Interleave sets for superset
                for (setIdx in 0 until maxSets) {
                    for (ex in groupExercises) {
                        if (setIdx < ex.setsCount) {
                            queue.add(
                                WorkoutStep(
                                    exercise = ex,
                                    setNumber = setIdx + 1,
                                    isSuperset = true,
                                    supersetPartnerNames = groupExercises.filter { it.id != ex.id }.map { it.name }
                                )
                            )
                        }
                    }
                }
                i = j
            } else {
                for (setIdx in 0 until current.setsCount) {
                    queue.add(
                        WorkoutStep(
                            exercise = current,
                            setNumber = setIdx + 1,
                            isSuperset = false
                        )
                    )
                }
                i++
            }
        }
        val savedOrder = viewModel.getWorkoutQueue(notebookId)
        val byKey = queue.associateBy { "${it.exercise.id}:${it.setNumber}" }
        if (savedOrder.size == queue.size && savedOrder.toSet() == byKey.keys) {
            val restored = savedOrder.mapNotNull { byKey[it] }
            queue.clear()
            queue.addAll(restored)
        }
        queue
    }

    if (executionQueue.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.no_exercises_in_workout),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.add_exercises_first),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.back))
                }
            }
        }
        return
    }

    val compatibleProgress = remember(exerciseConfigs) {
        val saved = viewModel.getWorkoutQueue(notebookId)
        saved.isEmpty() || saved.toSet() == executionQueue.map { "${it.exercise.id}:${it.setNumber}" }.toSet()
    }
    val savedIndex = remember(notebookId, compatibleProgress) {
        if (compatibleProgress) viewModel.getSavedWorkoutStepIndex(notebookId) else 0
    }
    var currentStepIndex by remember {
        mutableStateOf((savedIndex + if (compatibleProgress && viewModel.getCompletedWorkoutSteps(notebookId)?.contains(savedIndex) == true) 1 else 0)
            .coerceIn(0, executionQueue.lastIndex))
    }

    LaunchedEffect(currentStepIndex, notebookId) {
        if (notebookId > 0L) {
            viewModel.saveWorkoutStepIndex(notebookId, currentStepIndex)
        }
    }

    val currentStep = executionQueue.getOrNull(currentStepIndex)

    // Log tracking
    val completedSteps = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            val restored = if (compatibleProgress) viewModel.getCompletedWorkoutSteps(notebookId) ?: (0 until savedIndex).toSet() else emptySet()
            restored.filter { it in executionQueue.indices }.forEach { this[it] = true }
        }
    }
    val startTime = remember(notebookId) { if (compatibleProgress) viewModel.getWorkoutStartTime(notebookId) else System.currentTimeMillis() }
    LaunchedEffect(currentStepIndex, executionQueue.toList(), completedSteps.toMap()) {
        viewModel.saveWorkoutProgress(notebookId,
            executionQueue.map { "${it.exercise.id}:${it.setNumber}" },
            completedSteps.filterValues { it }.keys, startTime)
    }

    // Timing Engine State
    var isTimerRunning by rememberSaveable { mutableStateOf(false) }
    var secondsRemaining by rememberSaveable { mutableStateOf(0) }
    var isRestingState by rememberSaveable { mutableStateOf(false) } // true if in post-set rest countdown
    var isPrepState by rememberSaveable { mutableStateOf(false) } // true if in pre-timer preparation countdown
    var prepSecondsRemaining by rememberSaveable { mutableStateOf(0) }
    var currentExerciseWeight by rememberSaveable { mutableStateOf(0.0) }
    var isAwaitingConfirmation by rememberSaveable { mutableStateOf(true) }

    // Live Ongoing Notification Manager for Active Workout
    LaunchedEffect(currentStepIndex, isRestingState, isPrepState, prepSecondsRemaining, secondsRemaining, isTimerRunning) {
        val step = executionQueue.getOrNull(currentStepIndex)
        val statusText = when {
            step == null -> "Antrenman son aşamasında."
            isRestingState -> "⏸️ Mola: $secondsRemaining sn kaldı! (Sıradaki: ${executionQueue.getOrNull(currentStepIndex + 1)?.exercise?.name.orEmpty()})"
            isPrepState -> "🚀 Hazırlık: $prepSecondsRemaining sn..."
            step.exercise.type == "Timer" && isTimerRunning -> "⏱️ ${step.exercise.name} (Set ${step.setNumber}/${step.exercise.setsCount}) - $secondsRemaining sn"
            else -> "🏋️ ${step.exercise.name} (Set ${step.setNumber}/${step.exercise.setsCount}) ${if (step.exercise.weightKg > 0) "- ${step.exercise.weightKg} kg" else ""}"
        }
        xyz.younglion.flexilist.data.notifications.WorkoutNotificationManager.showOrUpdateWorkoutNotification(
            context = context,
            workoutName = workoutName,
            statusText = statusText,
            isPaused = !isTimerRunning && (step?.exercise?.type == "Timer")
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            xyz.younglion.flexilist.data.notifications.WorkoutNotificationManager.dismissWorkoutNotification(context)
        }
    }

    var isFinishing by remember { mutableStateOf(false) }

    fun finishWorkout() {
        if (isFinishing) return
        isFinishing = true
        isTimerRunning = false
        val endTime = System.currentTimeMillis()
        val summary = summarizeWorkout(executionQueue, completedSteps.filterValues { it }.keys)
        val log = xyz.younglion.flexilist.data.model.WorkoutLogEntity(
            notebookId = notebookId,
            workoutName = workoutName,
            startTime = startTime,
            endTime = endTime,
            totalDurationSeconds = ((endTime - startTime) / 1000).coerceAtLeast(0),
            totalSetsCompleted = summary.totalSets,
            totalWeightLiftedKg = summary.totalWeightKg,
            exerciseStatsJson = summary.exerciseStatsJson
        )
        viewModel.saveWorkoutSession(log, summary.completedExerciseIds,
            onSuccess = {
                SystemSoundAndNotificationHelper.dismissWorkoutNotification(context)
                Toast.makeText(context, context.getString(R.string.workout_saved), Toast.LENGTH_LONG).show()
                onDismiss()
            },
            onError = {
                isFinishing = false
                Toast.makeText(context, context.getString(R.string.workout_save_failed), Toast.LENGTH_LONG).show()
            }
        )
    }

    // Helper to transition state cleanly and prevent key race conditions
    fun moveToNextStep() {
        if (currentStepIndex < executionQueue.size - 1) {
            isRestingState = false
            currentStepIndex++
        } else {
            isRestingState = false
            finishWorkout()
        }
    }

    // Skip/Bunu Sonra Yap action
    fun skipCurrentExercise() {
        val step = currentStep ?: return
        val currentExId = step.exercise.id
        
        // Find if there is any OTHER exercise after the current step index
        val hasOtherExercises = executionQueue.any { it.exercise.id != currentExId && executionQueue.indexOf(it) > currentStepIndex }
        
        if (hasOtherExercises) {
            val newList = executionQueue.toMutableList()
            
            // Steps before the current step index remain unchanged
            val listBefore = newList.subList(0, currentStepIndex)
            // Steps from the current index onwards
            val listAfter = newList.subList(currentStepIndex, newList.size)
            
            val nonCurrentAfter = listAfter.filter { it.exercise.id != currentExId }
            val currentAfter = listAfter.filter { it.exercise.id == currentExId }
            
            // Re-build the executionQueue
            executionQueue.clear()
            executionQueue.addAll(listBefore + nonCurrentAfter + currentAfter)
            
            // After re-building, the currentStepIndex will now point to the first step of the NEXT exercise,
            // which has been moved up to the currentStepIndex spot!
            isAwaitingConfirmation = true
            isRestingState = false
            isTimerRunning = false
            isPrepState = false
            prepSecondsRemaining = 0
            
            val newStep = executionQueue.getOrNull(currentStepIndex)
            if (newStep != null) {
                currentExerciseWeight = newStep.exercise.weightKg
                secondsRemaining = if (newStep.exercise.type == "Timer") newStep.exercise.targetTimeSeconds else 0
            }
            
            Toast.makeText(context, context.getString(R.string.exercise_postponed, step.exercise.name), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.no_other_exercise_to_postpone), Toast.LENGTH_SHORT).show()
        }
    }

    // Initialize state on step change
    LaunchedEffect(currentStepIndex, isRestingState) {
        val step = executionQueue.getOrNull(currentStepIndex)
        val prevStep = if (currentStepIndex > 0) executionQueue.getOrNull(currentStepIndex - 1) else null
        if (step != null) {
            currentExerciseWeight = step.exercise.weightKg
            if (isRestingState) {
                secondsRemaining = step.exercise.restTimeSeconds
                isTimerRunning = true
                isPrepState = false
                prepSecondsRemaining = 0
                isAwaitingConfirmation = false
            } else {
                val isNewExercise = currentStepIndex == 0 || (prevStep != null && step.exercise.id != prevStep.exercise.id)
                if (isNewExercise) {
                    isAwaitingConfirmation = true
                    isTimerRunning = false
                    isPrepState = false
                    prepSecondsRemaining = 0
                    secondsRemaining = if (step.exercise.type == "Timer") step.exercise.targetTimeSeconds else 0
                } else {
                    isAwaitingConfirmation = false
                    if (step.exercise.type == "Timer") {
                        prepSecondsRemaining = 5
                        isPrepState = true
                        secondsRemaining = step.exercise.targetTimeSeconds
                        isTimerRunning = true
                    } else {
                        secondsRemaining = 0
                        isTimerRunning = false
                        isPrepState = false
                        prepSecondsRemaining = 0
                    }
                }
            }
        }
    }

    // Core Timer Countdown ticker loop
    LaunchedEffect(currentStepIndex, isTimerRunning, isRestingState, isPrepState, isAwaitingConfirmation) {
        if (isTimerRunning && !isAwaitingConfirmation) {
            if (isRestingState && secondsRemaining == 0) {
                moveToNextStep()
                return@LaunchedEffect
            }
            while (true) {
                delay(1000)
                if (isPrepState) {
                    if (prepSecondsRemaining > 0) {
                        prepSecondsRemaining--
                        val progressMsg = "HAZIRLAN: ${prepSecondsRemaining}sn kaldı"
                        SystemSoundAndNotificationHelper.showWorkoutNotification(context, workoutName, progressMsg)

                        if (prepSecondsRemaining == 0) {
                            SystemSoundAndNotificationHelper.playDing()
                            isPrepState = false
                        } else if (prepSecondsRemaining <= 3) {
                            SystemSoundAndNotificationHelper.playTick()
                        }
                    } else {
                        isPrepState = false
                    }
                } else if (secondsRemaining > 0) {
                    secondsRemaining--
                    val progressMsg = if (isRestingState) {
                        "MOLA: ${secondsRemaining}sn kaldı"
                    } else {
                        currentStep?.let { "${it.exercise.name} (Set ${it.setNumber}): ${secondsRemaining}sn" } ?: ""
                    }
                    SystemSoundAndNotificationHelper.showWorkoutNotification(context, workoutName, progressMsg)

                    if (secondsRemaining == 0) {
                        SystemSoundAndNotificationHelper.playDing()
                        if (isRestingState) {
                            // Rest is over, start next set/exercise
                            moveToNextStep()
                        } else {
                            // Timer exercise set is done, automatically enter rest state
                            completedSteps[currentStepIndex] = true
                            if (currentStepIndex < executionQueue.size - 1) {
                                isRestingState = true
                            } else {
                                // All finished!
                                isTimerRunning = false
                                finishWorkout()
                                break
                            }
                        }
                    } else if (secondsRemaining <= 3) {
                        SystemSoundAndNotificationHelper.playTick()
                    }
                } else {
                    if (isRestingState) moveToNextStep()
                    break
                }
            }
        }
    }

    // Dismiss notifications on clear
    DisposableEffect(Unit) {
        onDispose {
            SystemSoundAndNotificationHelper.dismissWorkoutNotification(context)
        }
    }

    // Visual Theme (Dark Distraction-free Slate Space)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Sleek slate space dark background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top bar summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White.copy(alpha = 0.6f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        workoutName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        stringResource(R.string.step_indicator, currentStepIndex + 1, executionQueue.size),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { finishWorkout() }, enabled = !isFinishing) {
                    Icon(Icons.Default.DoneAll, stringResource(R.string.finish_workout), tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { completedSteps.count { it.value }.toFloat() / executionQueue.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            if (currentStep != null) {
                val ex = currentStep.exercise

                // 2. Muscle Group Visualizer & Exercise details
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isAwaitingConfirmation) {
                        Text(
                            stringResource(R.string.next_exercise_header),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            ex.name.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MuscleGroupVisualizer(targetMuscles = ex.targetMuscles)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.sets_count), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                Text("${ex.setsCount}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.1f)))
                            if (ex.type == "Reps") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.target_reps), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    Text("${ex.targetReps}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.target_time), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    Text("${ex.targetTimeSeconds}s", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.1f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.weight), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                Text("${ex.weightKg} kg", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    } else if (isPrepState) {
                        Text(
                            stringResource(R.string.get_ready),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF59E0B), // Vibrant amber gold
                            fontSize = 18.sp,
                            letterSpacing = 3.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.1f), CircleShape)
                                .border(4.dp, Color(0xFFF59E0B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$prepSecondsRemaining",
                                fontWeight = FontWeight.Black,
                                fontSize = 64.sp,
                                color = Color(0xFFF59E0B)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.next_label, ex.name),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.set_progress, currentStep.setNumber, ex.setsCount),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    } else if (isRestingState) {
                        Text(
                            stringResource(R.string.rest_time),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${secondsRemaining}s",
                            fontWeight = FontWeight.Black,
                            fontSize = 64.sp,
                            color = Color.White
                        )
                        Text(
                            stringResource(R.string.next_label, executionQueue.getOrNull(currentStepIndex + 1)?.let { next ->
                                "${next.exercise.name} (${next.setNumber}/${next.exercise.setsCount})"
                            }.orEmpty()),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Title of current action
                        Text(
                            ex.name.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        if (currentStep.isSuperset) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.hybrid_superset, ex.supersetGroupId),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Target Info Cards Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.sets_count), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                Text("${currentStep.setNumber} / ${ex.setsCount}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                            Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.1f)))
                            
                            if (ex.type == "Reps") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.target_reps), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    Text("${ex.targetReps}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.target_time), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    Text("${secondsRemaining}s", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.1f)))
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.weight), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                Text("${currentExerciseWeight} kg", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // 3. Bottom controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isAwaitingConfirmation) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    isAwaitingConfirmation = false
                                    val step = executionQueue.getOrNull(currentStepIndex)
                                    if (step != null) {
                                        if (step.exercise.type == "Timer") {
                                            prepSecondsRemaining = 5
                                            isPrepState = true
                                            secondsRemaining = step.exercise.targetTimeSeconds
                                            isTimerRunning = true
                                        } else {
                                            secondsRemaining = 0
                                            isTimerRunning = false
                                            isPrepState = false
                                            prepSecondsRemaining = 0
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("confirm_start_exercise_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.start_exercise_btn), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }

                            OutlinedButton(
                                onClick = { skipCurrentExercise() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("skip_exercise_start_screen_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.do_this_later), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    } else if (isPrepState) {
                        Button(
                            onClick = {
                                isPrepState = false
                                prepSecondsRemaining = 0
                                val step = executionQueue.getOrNull(currentStepIndex)
                                if (step != null) {
                                    secondsRemaining = step.exercise.targetTimeSeconds
                                    isTimerRunning = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("skip_prep_countdown_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.skip_countdown), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    } else if (isRestingState) {
                        // Rest screen quick action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { secondsRemaining += 15 }) {
                                Text(stringResource(R.string.add_15s_rest), color = MaterialTheme.colorScheme.primary)
                            }
                            Button(
                                onClick = {
                                    moveToNextStep()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text(stringResource(R.string.skip_rest), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Action buttons based on exercise type
                        if (ex.type == "Reps") {
                            Button(
                                onClick = {
                                    completedSteps[currentStepIndex] = true
                                    if (currentStepIndex < executionQueue.size - 1) {
                                        isRestingState = true
                                    } else {
                                        finishWorkout()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("complete_set_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.complete_set), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                        } else {
                            // Timer control buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { isTimerRunning = !isTimerRunning },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTimerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isTimerRunning) stringResource(R.string.pause) else stringResource(R.string.resume_start), fontWeight = FontWeight.ExtraBold)
                                }

                                Button(
                                    onClick = {
                                        completedSteps[currentStepIndex] = true
                                        if (currentStepIndex < executionQueue.size - 1) {
                                            isRestingState = true
                                        } else {
                                            finishWorkout()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(stringResource(R.string.skip_complete), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleGroupVisualizer(targetMuscles: List<String>) {
    // Beautiful Canvas rendering custom high-fidelity cyberpunk anatomical model with scanning lines
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f - 10.dp.toPx()

        // 1. Draw elegant background scanning grid lines
        val gridLines = 5
        for (i in 0..gridLines) {
            val ratio = i.toFloat() / gridLines
            // Vertical grid lines
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.4f),
                start = Offset(w * ratio, 0f),
                end = Offset(w * ratio, h),
                strokeWidth = 1f
            )
            // Horizontal grid lines
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.4f),
                start = Offset(0f, h * ratio),
                end = Offset(w, h * ratio),
                strokeWidth = 1f
            )
        }

        // Draw outer radar rings
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.05f),
            radius = 110.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.08f),
            radius = 80.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // 2. Draw Body Base Outline (Cyberpunk anatomical wireframe)
        // Head / Helmet Contour
        drawCircle(
            color = Color(0xFF64748B).copy(alpha = 0.25f),
            radius = 16.dp.toPx(),
            center = Offset(cx, cy - 65.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF64748B).copy(alpha = 0.4f),
            radius = 16.dp.toPx(),
            center = Offset(cx, cy - 65.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Spine & Ribcage wireframe
        drawLine(
            color = Color(0xFF64748B).copy(alpha = 0.3f),
            start = Offset(cx, cy - 48.dp.toPx()),
            end = Offset(cx, cy + 15.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )
        // Shoulders base
        drawLine(
            color = Color(0xFF64748B).copy(alpha = 0.3f),
            start = Offset(cx - 32.dp.toPx(), cy - 44.dp.toPx()),
            end = Offset(cx + 32.dp.toPx(), cy - 44.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Hips base
        drawLine(
            color = Color(0xFF64748B).copy(alpha = 0.3f),
            start = Offset(cx - 20.dp.toPx(), cy + 15.dp.toPx()),
            end = Offset(cx + 20.dp.toPx(), cy + 15.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 3. Render High-fidelity Muscle Zones with translucent active fills & vibrant glows
        targetMuscles.forEach { muscle ->
            val cleanMuscle = muscle.trim()
            val colorToken = when (cleanMuscle.lowercase()) {
                "göğüs", "chest" -> Color(0xFFEF4444) // Vibrant Neon Red
                "sırt", "back" -> Color(0xFF3B82F6)  // Bright Cobalt Blue
                "bacak", "legs" -> Color(0xFF10B981)  // Neon Emerald Green
                "omuz", "shoulders" -> Color(0xFFF59E0B)  // Vivid Amber
                "kollar", "arms" -> Color(0xFFD946EF) // Laser Magenta
                "karın", "abs", "core" -> Color(0xFF06B6D4)  // Cyan Tech Blue
                else -> Color(0xFF38BDF8)
            }

            // Draw a halo ring around active targets
            drawCircle(
                color = colorToken.copy(alpha = 0.04f),
                radius = 90.dp.toPx(),
                center = Offset(cx, cy)
            )

            when (cleanMuscle.lowercase()) {
                "göğüs", "chest" -> {
                    // Left Chest Plate
                    drawRoundRect(
                        color = colorToken.copy(alpha = 0.35f),
                        topLeft = Offset(cx - 28.dp.toPx(), cy - 38.dp.toPx()),
                        size = Size(24.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = colorToken,
                        topLeft = Offset(cx - 28.dp.toPx(), cy - 38.dp.toPx()),
                        size = Size(24.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Right Chest Plate
                    drawRoundRect(
                        color = colorToken.copy(alpha = 0.35f),
                        topLeft = Offset(cx + 4.dp.toPx(), cy - 38.dp.toPx()),
                        size = Size(24.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = colorToken,
                        topLeft = Offset(cx + 4.dp.toPx(), cy - 38.dp.toPx()),
                        size = Size(24.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                "sırt", "back" -> {
                    // Massive Upper Back Wing Traps Left
                    val leftTrap = Path().apply {
                        moveTo(cx - 4.dp.toPx(), cy - 42.dp.toPx())
                        lineTo(cx - 32.dp.toPx(), cy - 40.dp.toPx())
                        lineTo(cx - 12.dp.toPx(), cy - 10.dp.toPx())
                        close()
                    }
                    drawPath(leftTrap, color = colorToken.copy(alpha = 0.35f))
                    drawPath(leftTrap, color = colorToken, style = Stroke(width = 2.dp.toPx()))

                    // Right Trap Wing
                    val rightTrap = Path().apply {
                        moveTo(cx + 4.dp.toPx(), cy - 42.dp.toPx())
                        lineTo(cx + 32.dp.toPx(), cy - 40.dp.toPx())
                        lineTo(cx + 12.dp.toPx(), cy - 10.dp.toPx())
                        close()
                    }
                    drawPath(rightTrap, color = colorToken.copy(alpha = 0.35f))
                    drawPath(rightTrap, color = colorToken, style = Stroke(width = 2.dp.toPx()))
                }
                "bacak", "legs" -> {
                    // Left Quad
                    drawRoundRect(
                        color = colorToken.copy(alpha = 0.35f),
                        topLeft = Offset(cx - 18.dp.toPx(), cy + 18.dp.toPx()),
                        size = Size(14.dp.toPx(), 36.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = colorToken,
                        topLeft = Offset(cx - 18.dp.toPx(), cy + 18.dp.toPx()),
                        size = Size(14.dp.toPx(), 36.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Right Quad
                    drawRoundRect(
                        color = colorToken.copy(alpha = 0.35f),
                        topLeft = Offset(cx + 4.dp.toPx(), cy + 18.dp.toPx()),
                        size = Size(14.dp.toPx(), 36.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = colorToken,
                        topLeft = Offset(cx + 4.dp.toPx(), cy + 18.dp.toPx()),
                        size = Size(14.dp.toPx(), 36.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Left Calf
                    drawRoundRect(
                        color = colorToken.copy(alpha = 0.25f),
                        topLeft = Offset(cx - 16.dp.toPx(), cy + 58.dp.toPx()),
                        size = Size(10.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = colorToken,
                        topLeft = Offset(cx - 16.dp.toPx(), cy + 58.dp.toPx()),
                        size = Size(10.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    // Right Calf
                    drawRoundRect(
                        color = colorToken.copy(alpha = 0.25f),
                        topLeft = Offset(cx + 6.dp.toPx(), cy + 58.dp.toPx()),
                        size = Size(10.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = colorToken,
                        topLeft = Offset(cx + 6.dp.toPx(), cy + 58.dp.toPx()),
                        size = Size(10.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                "omuz", "shoulders" -> {
                    // Left Deltoid Cap
                    drawCircle(
                        color = colorToken.copy(alpha = 0.4f),
                        radius = 8.dp.toPx(),
                        center = Offset(cx - 32.dp.toPx(), cy - 44.dp.toPx())
                    )
                    drawCircle(
                        color = colorToken,
                        radius = 8.dp.toPx(),
                        center = Offset(cx - 32.dp.toPx(), cy - 44.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Right Deltoid Cap
                    drawCircle(
                        color = colorToken.copy(alpha = 0.4f),
                        radius = 8.dp.toPx(),
                        center = Offset(cx + 32.dp.toPx(), cy - 44.dp.toPx())
                    )
                    drawCircle(
                        color = colorToken,
                        radius = 8.dp.toPx(),
                        center = Offset(cx + 32.dp.toPx(), cy - 44.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                "kollar", "arms" -> {
                    // Left Upper Arm
                    drawLine(
                        color = colorToken,
                        start = Offset(cx - 34.dp.toPx(), cy - 36.dp.toPx()),
                        end = Offset(cx - 42.dp.toPx(), cy - 10.dp.toPx()),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Left Forearm
                    drawLine(
                        color = colorToken.copy(alpha = 0.7f),
                        start = Offset(cx - 42.dp.toPx(), cy - 8.dp.toPx()),
                        end = Offset(cx - 44.dp.toPx(), cy + 16.dp.toPx()),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Right Upper Arm
                    drawLine(
                        color = colorToken,
                        start = Offset(cx + 34.dp.toPx(), cy - 36.dp.toPx()),
                        end = Offset(cx + 42.dp.toPx(), cy - 10.dp.toPx()),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Right Forearm
                    drawLine(
                        color = colorToken.copy(alpha = 0.7f),
                        start = Offset(cx + 42.dp.toPx(), cy - 8.dp.toPx()),
                        end = Offset(cx + 44.dp.toPx(), cy + 16.dp.toPx()),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                "karın", "abs", "core" -> {
                    // Tech Core Abdominal Wall
                    drawRoundRect(
                        color = colorToken.copy(alpha = 0.3f),
                        topLeft = Offset(cx - 8.dp.toPx(), cy - 16.dp.toPx()),
                        size = Size(16.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawRoundRect(
                        color = colorToken,
                        topLeft = Offset(cx - 8.dp.toPx(), cy - 16.dp.toPx()),
                        size = Size(16.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    // Ab Lines
                    drawLine(colorToken, Offset(cx - 6.dp.toPx(), cy - 10.dp.toPx()), Offset(cx + 6.dp.toPx(), cy - 10.dp.toPx()), 2.dp.toPx())
                    drawLine(colorToken, Offset(cx - 6.dp.toPx(), cy - 2.dp.toPx()), Offset(cx + 6.dp.toPx(), cy - 2.dp.toPx()), 2.dp.toPx())
                    drawLine(colorToken, Offset(cx - 6.dp.toPx(), cy + 6.dp.toPx()), Offset(cx + 6.dp.toPx(), cy + 6.dp.toPx()), 2.dp.toPx())
                }
            }
        }
    }
}

data class PlayableExercise(
    val id: Long,
    val name: String,
    val type: String,
    val setsCount: Int,
    val targetReps: Int,
    val targetTimeSeconds: Int,
    val restTimeSeconds: Int,
    val weightKg: Double,
    val targetMuscles: List<String>,
    val supersetGroupId: String
)

data class WorkoutStep(
    val exercise: PlayableExercise,
    val setNumber: Int,
    val isSuperset: Boolean,
    val supersetPartnerNames: List<String> = emptyList()
)
