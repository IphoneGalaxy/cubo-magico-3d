package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cuboMestreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cubo_mestre_progress"
)

enum class StepStatus(val labelPt: String) {
    NOT_STARTED("Não iniciada"),
    IN_PROGRESS("Em andamento"),
    COMPLETED("Concluída")
}

data class UserProgressState(
    val completedSteps: Set<Int> = emptySet(),
    val inProgressSteps: Set<Int> = setOf(1),
    val completedCaseIds: Set<String> = emptySet(),
    val lastVisitedStep: Int = 1,
    val lastVisitedCaseId: String = "s1_c1",
    val animationSpeedMultiplier: Float = 1.0f,
    val isDarkTheme: Boolean = true,
    val highlightTargetPieces: Boolean = true
) {
    fun getStepStatus(stepNumber: Int): StepStatus = when {
        completedSteps.contains(stepNumber) -> StepStatus.COMPLETED
        inProgressSteps.contains(stepNumber) || stepNumber == lastVisitedStep -> StepStatus.IN_PROGRESS
        else -> StepStatus.NOT_STARTED
    }

    val completionFraction: Float
        get() = (completedSteps.size.coerceAtMost(7)) / 7f
}

class ProgressRepository(private val context: Context) {

    private object Keys {
        val COMPLETED_STEPS = stringSetPreferencesKey("completed_steps")
        val IN_PROGRESS_STEPS = stringSetPreferencesKey("in_progress_steps")
        val COMPLETED_CASES = stringSetPreferencesKey("completed_cases")
        val LAST_STEP = intPreferencesKey("last_step")
        val LAST_CASE = stringPreferencesKey("last_case")
        val SPEED_MULTIPLIER = floatPreferencesKey("speed_multiplier")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val HIGHLIGHT_PIECES = booleanPreferencesKey("highlight_pieces")
    }

    val progressFlow: Flow<UserProgressState> = context.cuboMestreDataStore.data.map { prefs ->
        val completedSteps = prefs[Keys.COMPLETED_STEPS]
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()
        val inProgressSteps = prefs[Keys.IN_PROGRESS_STEPS]
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: setOf(1)
        val completedCases = prefs[Keys.COMPLETED_CASES] ?: emptySet()
        val lastStep = prefs[Keys.LAST_STEP] ?: 1
        val lastCase = prefs[Keys.LAST_CASE] ?: "s1_c1"
        val speed = prefs[Keys.SPEED_MULTIPLIER] ?: 1.0f
        val isDark = prefs[Keys.IS_DARK_THEME] ?: true
        val highlight = prefs[Keys.HIGHLIGHT_PIECES] ?: true

        UserProgressState(
            completedSteps = completedSteps,
            inProgressSteps = inProgressSteps,
            completedCaseIds = completedCases,
            lastVisitedStep = lastStep,
            lastVisitedCaseId = lastCase,
            animationSpeedMultiplier = speed,
            isDarkTheme = isDark,
            highlightTargetPieces = highlight
        )
    }

    suspend fun recordStepVisited(stepNumber: Int, caseId: String) {
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.LAST_STEP] = stepNumber
            prefs[Keys.LAST_CASE] = caseId
            val currentInProgress = prefs[Keys.IN_PROGRESS_STEPS]?.toMutableSet() ?: mutableSetOf("1")
            currentInProgress.add(stepNumber.toString())
            prefs[Keys.IN_PROGRESS_STEPS] = currentInProgress
        }
    }

    suspend fun markCaseCompleted(stepNumber: Int, caseId: String, allStepCaseIds: List<String>) {
        context.cuboMestreDataStore.edit { prefs ->
            val currentCases = prefs[Keys.COMPLETED_CASES]?.toMutableSet() ?: mutableSetOf()
            currentCases.add(caseId)
            prefs[Keys.COMPLETED_CASES] = currentCases

            val currentInProgress = prefs[Keys.IN_PROGRESS_STEPS]?.toMutableSet() ?: mutableSetOf("1")
            currentInProgress.add(stepNumber.toString())

            // Se concluiu ao menos um exercício prático da etapa ou todos os casos, marca etapa como concluída
            val currentCompletedSteps = prefs[Keys.COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
            if (allStepCaseIds.any { currentCases.contains(it) }) {
                currentCompletedSteps.add(stepNumber.toString())
                if (stepNumber < 7) {
                    currentInProgress.add((stepNumber + 1).toString())
                }
            }
            prefs[Keys.IN_PROGRESS_STEPS] = currentInProgress
            prefs[Keys.COMPLETED_STEPS] = currentCompletedSteps
        }
    }

    suspend fun markStepCompleted(stepNumber: Int) {
        context.cuboMestreDataStore.edit { prefs ->
            val currentCompleted = prefs[Keys.COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
            currentCompleted.add(stepNumber.toString())
            prefs[Keys.COMPLETED_STEPS] = currentCompleted

            val currentInProgress = prefs[Keys.IN_PROGRESS_STEPS]?.toMutableSet() ?: mutableSetOf("1")
            if (stepNumber < 7) {
                currentInProgress.add((stepNumber + 1).toString())
            }
            prefs[Keys.IN_PROGRESS_STEPS] = currentInProgress
        }
    }

    suspend fun resetStepProgress(stepNumber: Int, stepCaseIds: List<String>) {
        context.cuboMestreDataStore.edit { prefs ->
            val currentCompleted = prefs[Keys.COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
            currentCompleted.remove(stepNumber.toString())
            prefs[Keys.COMPLETED_STEPS] = currentCompleted

            val currentCases = prefs[Keys.COMPLETED_CASES]?.toMutableSet() ?: mutableSetOf()
            currentCases.removeAll(stepCaseIds.toSet())
            prefs[Keys.COMPLETED_CASES] = currentCases
        }
    }

    suspend fun setAnimationSpeed(multiplier: Float) {
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.SPEED_MULTIPLIER] = multiplier.coerceIn(0.4f, 2.0f)
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = isDark
        }
    }

    suspend fun setHighlightTargetPieces(enabled: Boolean) {
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.HIGHLIGHT_PIECES] = enabled
        }
    }
}
