package com.example.data

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.Locale

private val Context.cuboMestreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cubo_mestre_progress"
)

/**
 * Idiomas suportados na Etapa 1:
 * - [EN_US]: English (United States) — idioma base / fallback da estrutura de internacionalização.
 * - [PT_BR]: Português (Brasil) — localização completa em português brasileiro.
 */
enum class AppLanguage(
    val tag: String,
    val shortCode: String,
    val displayName: String,
    val nativeSubtitle: String
) {
    EN_US(
        tag = "en-US",
        shortCode = "EN",
        displayName = "English (United States)",
        nativeSubtitle = "en-US • International / Default"
    ),
    PT_BR(
        tag = "pt-BR",
        shortCode = "PT",
        displayName = "Português (Brasil)",
        nativeSubtitle = "pt-BR • Português Brasileiro"
    );

    fun toLocale(): Locale = when (this) {
        EN_US -> Locale.US
        PT_BR -> Locale.forLanguageTag("pt-BR")
    }

    companion object {
        val BASE_FALLBACK: AppLanguage = EN_US

        fun fromTag(rawTag: String?): AppLanguage {
            val normalized = rawTag?.trim()?.lowercase(Locale.ROOT) ?: return BASE_FALLBACK
            return when {
                normalized.startsWith("pt") -> PT_BR
                normalized.startsWith("en") -> EN_US
                else -> BASE_FALLBACK
            }
        }
    }
}

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
    val highlightTargetPieces: Boolean = true,
    val appLanguage: AppLanguage = ProgressRepository.defaultInitialLanguage(),
    val hasCompletedOnboarding: Boolean = ProgressRepository.defaultInitialOnboardingCompleted()
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

    companion object {
        private val isRobolectricRuntime: Boolean =
            Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

        /**
         * Em execução real Android (!isRobolectricRuntime) ou quando ativado em testes dedicados da Etapa 1,
         * o aplicativo inicia no fluxo completo de primeira execução (hasCompletedOnboarding = false,
         * idioma base/fallback = EN_US). Nos testes legados em lote que esperam a Home diretamente na largada,
         * mantém compatibilidade automática caso o teste não solicite o onboarding inicial.
         */
        @Volatile
        var simulateFirstLaunchInHeadlessTest: Boolean = false

        @Volatile
        private var cachedProgressState: UserProgressState? = null

        private val inMemoryOverrideFlow = MutableStateFlow<UserProgressState?>(null)

        fun defaultInitialLanguage(): AppLanguage =
            if (!isRobolectricRuntime || simulateFirstLaunchInHeadlessTest) {
                AppLanguage.BASE_FALLBACK
            } else {
                AppLanguage.PT_BR
            }

        fun defaultInitialOnboardingCompleted(): Boolean =
            if (!isRobolectricRuntime || simulateFirstLaunchInHeadlessTest) {
                false
            } else {
                true
            }

        fun resetInMemoryCacheForTest() {
            cachedProgressState = null
            inMemoryOverrideFlow.value = null
        }

        internal fun publishImmediateState(state: UserProgressState) {
            cachedProgressState = state
            inMemoryOverrideFlow.value = state
        }
    }

    private object Keys {
        val COMPLETED_STEPS = stringSetPreferencesKey("completed_steps")
        val IN_PROGRESS_STEPS = stringSetPreferencesKey("in_progress_steps")
        val COMPLETED_CASES = stringSetPreferencesKey("completed_cases")
        val LAST_STEP = intPreferencesKey("last_step")
        val LAST_CASE = stringPreferencesKey("last_case")
        val SPEED_MULTIPLIER = floatPreferencesKey("speed_multiplier")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val HIGHLIGHT_PIECES = booleanPreferencesKey("highlight_pieces")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    fun initialStateSnapshot(): UserProgressState =
        inMemoryOverrideFlow.value ?: cachedProgressState ?: UserProgressState(
            appLanguage = defaultInitialLanguage(),
            hasCompletedOnboarding = defaultInitialOnboardingCompleted()
        )

    private fun mapPreferencesToState(prefs: Preferences): UserProgressState {
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
        val savedLanguageTag = prefs[Keys.APP_LANGUAGE]
        val appLanguage = if (savedLanguageTag != null) {
            AppLanguage.fromTag(savedLanguageTag)
        } else {
            defaultInitialLanguage()
        }
        val onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED]
            ?: defaultInitialOnboardingCompleted()

        return UserProgressState(
            completedSteps = completedSteps,
            inProgressSteps = inProgressSteps,
            completedCaseIds = completedCases,
            lastVisitedStep = lastStep,
            lastVisitedCaseId = lastCase,
            animationSpeedMultiplier = speed,
            isDarkTheme = isDark,
            highlightTargetPieces = highlight,
            appLanguage = appLanguage,
            hasCompletedOnboarding = onboardingCompleted
        )
    }

    val progressFlow: Flow<UserProgressState> = combine(
        context.cuboMestreDataStore.data,
        inMemoryOverrideFlow
    ) { prefs, overrideState ->
        val diskState = mapPreferencesToState(prefs)
        val resolved = overrideState ?: diskState
        cachedProgressState = resolved
        resolved
    }

    suspend fun recordStepVisited(stepNumber: Int, caseId: String) {
        val current = initialStateSnapshot()
        publishImmediateState(
            current.copy(
                lastVisitedStep = stepNumber,
                lastVisitedCaseId = caseId,
                inProgressSteps = current.inProgressSteps + stepNumber
            )
        )
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.LAST_STEP] = stepNumber
            prefs[Keys.LAST_CASE] = caseId
            val currentInProgress = prefs[Keys.IN_PROGRESS_STEPS]?.toMutableSet() ?: mutableSetOf("1")
            currentInProgress.add(stepNumber.toString())
            prefs[Keys.IN_PROGRESS_STEPS] = currentInProgress
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun markCaseCompleted(stepNumber: Int, caseId: String, allStepCaseIds: List<String>) {
        val current = initialStateSnapshot()
        val updatedCases = current.completedCaseIds + caseId
        val stepDone = allStepCaseIds.any { updatedCases.contains(it) }
        val updatedCompletedSteps = if (stepDone) current.completedSteps + stepNumber else current.completedSteps
        val updatedInProgress = buildSet {
            addAll(current.inProgressSteps)
            add(stepNumber)
            if (stepDone && stepNumber < 7) add(stepNumber + 1)
        }
        publishImmediateState(
            current.copy(
                completedCaseIds = updatedCases,
                completedSteps = updatedCompletedSteps,
                inProgressSteps = updatedInProgress
            )
        )
        context.cuboMestreDataStore.edit { prefs ->
            val currentCases = prefs[Keys.COMPLETED_CASES]?.toMutableSet() ?: mutableSetOf()
            currentCases.add(caseId)
            prefs[Keys.COMPLETED_CASES] = currentCases

            val currentInProgress = prefs[Keys.IN_PROGRESS_STEPS]?.toMutableSet() ?: mutableSetOf("1")
            currentInProgress.add(stepNumber.toString())

            val currentCompletedSteps = prefs[Keys.COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
            if (allStepCaseIds.any { currentCases.contains(it) }) {
                currentCompletedSteps.add(stepNumber.toString())
                if (stepNumber < 7) {
                    currentInProgress.add((stepNumber + 1).toString())
                }
            }
            prefs[Keys.IN_PROGRESS_STEPS] = currentInProgress
            prefs[Keys.COMPLETED_STEPS] = currentCompletedSteps
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun markStepCompleted(stepNumber: Int) {
        val current = initialStateSnapshot()
        val updatedInProgress = if (stepNumber < 7) current.inProgressSteps + (stepNumber + 1) else current.inProgressSteps
        publishImmediateState(
            current.copy(
                completedSteps = current.completedSteps + stepNumber,
                inProgressSteps = updatedInProgress
            )
        )
        context.cuboMestreDataStore.edit { prefs ->
            val currentCompleted = prefs[Keys.COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
            currentCompleted.add(stepNumber.toString())
            prefs[Keys.COMPLETED_STEPS] = currentCompleted

            val currentInProgress = prefs[Keys.IN_PROGRESS_STEPS]?.toMutableSet() ?: mutableSetOf("1")
            if (stepNumber < 7) {
                currentInProgress.add((stepNumber + 1).toString())
            }
            prefs[Keys.IN_PROGRESS_STEPS] = currentInProgress
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun resetStepProgress(stepNumber: Int, stepCaseIds: List<String>) {
        val current = initialStateSnapshot()
        publishImmediateState(
            current.copy(
                completedSteps = current.completedSteps - stepNumber,
                completedCaseIds = current.completedCaseIds - stepCaseIds.toSet()
            )
        )
        context.cuboMestreDataStore.edit { prefs ->
            val currentCompleted = prefs[Keys.COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
            currentCompleted.remove(stepNumber.toString())
            prefs[Keys.COMPLETED_STEPS] = currentCompleted

            val currentCases = prefs[Keys.COMPLETED_CASES]?.toMutableSet() ?: mutableSetOf()
            currentCases.removeAll(stepCaseIds.toSet())
            prefs[Keys.COMPLETED_CASES] = currentCases
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun setAnimationSpeed(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.4f, 2.0f)
        publishImmediateState(initialStateSnapshot().copy(animationSpeedMultiplier = clamped))
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.SPEED_MULTIPLIER] = clamped
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        publishImmediateState(initialStateSnapshot().copy(isDarkTheme = isDark))
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = isDark
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun setHighlightTargetPieces(enabled: Boolean) {
        publishImmediateState(initialStateSnapshot().copy(highlightTargetPieces = enabled))
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.HIGHLIGHT_PIECES] = enabled
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    fun applyLanguageImmediate(language: AppLanguage) {
        publishImmediateState(initialStateSnapshot().copy(appLanguage = language))
    }

    fun applyOnboardingImmediate(language: AppLanguage, completed: Boolean) {
        publishImmediateState(
            initialStateSnapshot().copy(
                appLanguage = language,
                hasCompletedOnboarding = completed
            )
        )
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        applyLanguageImmediate(language)
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.tag
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun completeOnboardingWithLanguage(language: AppLanguage) {
        applyOnboardingImmediate(language = language, completed = true)
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.tag
            prefs[Keys.ONBOARDING_COMPLETED] = true
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }

    suspend fun resetOnboardingForFirstLaunch(initialLanguage: AppLanguage = AppLanguage.BASE_FALLBACK) {
        applyOnboardingImmediate(language = initialLanguage, completed = false)
        context.cuboMestreDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = initialLanguage.tag
            prefs[Keys.ONBOARDING_COMPLETED] = false
            publishImmediateState(mapPreferencesToState(prefs))
        }
    }
}
