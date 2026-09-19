package com.arya.reflection

import com.arya.actions.ActionType
import com.arya.diagnostics.AryaLogger
import com.arya.perception.ChangeLevel
import com.arya.perception.ScreenChangeDetector
import com.arya.perception.ScreenState

/**
 * Production-grade Cognitive Reflection Engine for ARYA.
 *
 * Implements the formula:
 * PreviousState + Action + CurrentState + ExpectedEffect = ReflectionRecord
 *
 * Evaluates outcome, classifies failures, detects stuck loops, and recommends recovery actions.
 */
class ReflectionEngine(
    private val changeDetector: ScreenChangeDetector = ScreenChangeDetector(),
    private val maxConsecutiveFailures: Int = 3
) {

    private val recentHashes = ArrayDeque<String>()
    private var consecutiveFailures = 0

    /**
     * Reflects upon the transition between PreviousState and CurrentState after Action.
     */
    fun reflect(
        previousState: ScreenState?,
        action: ActionType,
        currentState: ScreenState,
        expectedEffect: String? = null
    ): ReflectionRecord {
        val diff = changeDetector.detectChange(previousState, currentState)

        // Check for stuck loops (identical structural state hash repeated)
        recentHashes.addLast(currentState.structuralHash)
        if (recentHashes.size > 8) {
            recentHashes.removeFirst()
        }
        val identicalCount = recentHashes.count { it == currentState.structuralHash }
        val isLoop = identicalCount >= 3 && action !is ActionType.Wait

        if (isLoop) {
            consecutiveFailures++
            AryaLogger.e(TAG, "Stuck loop detected: state '${currentState.structuralHash}' repeated $identicalCount times.")
            return ReflectionRecord(
                verdict = ReflectionVerdict.FAILURE,
                nextStep = if (consecutiveFailures >= maxConsecutiveFailures) RecommendedNextStep.ABORT else RecommendedNextStep.RECOVER_BACKSTEP,
                confidence = 0.95f,
                rationale = "Stuck loop detected: repeated identical screen state ($identicalCount occurrences).",
                consecutiveFailures = consecutiveFailures,
                isLoopDetected = true
            )
        }

        // Check for app crash (sudden transition back to home launcher or crash dialog)
        if (previousState != null && previousState.packageName != "com.android.launcher" && currentState.packageName == "com.android.launcher") {
            consecutiveFailures++
            AryaLogger.e(TAG, "App crash or unexpected launcher return: ${previousState.packageName} -> ${currentState.packageName}")
            return ReflectionRecord(
                verdict = ReflectionVerdict.APP_CRASH,
                nextStep = RecommendedNextStep.REPLAN,
                confidence = 0.90f,
                rationale = "Application terminated or crashed to home screen.",
                consecutiveFailures = consecutiveFailures
            )
        }

        // Check for unexpected modal dialog / permission prompt
        if (diff.dialogAppeared || currentState.isDialogShowing) {
            AryaLogger.w(TAG, "Modal dialog or permission prompt occluding screen.")
            return ReflectionRecord(
                verdict = ReflectionVerdict.POPUP_ENCOUNTERED,
                nextStep = RecommendedNextStep.DISMISS_POPUP,
                confidence = 0.90f,
                rationale = "Unexpected modal dialog or permission prompt detected.",
                consecutiveFailures = consecutiveFailures
            )
        }

        // Check for IME keyboard occlusion
        if (diff.keyboardToggled && currentState.isKeyboardVisible) {
            AryaLogger.d(TAG, "Soft keyboard active.")
            return ReflectionRecord(
                verdict = ReflectionVerdict.KEYBOARD_OCCLUSION,
                nextStep = RecommendedNextStep.HIDE_KEYBOARD,
                confidence = 0.85f,
                rationale = "Soft keyboard toggled and may occlude interactive targets.",
                consecutiveFailures = consecutiveFailures
            )
        }

        // Check for zero UI change after an interactive tap or long tap
        if (diff.changeLevel == ChangeLevel.NONE && (action is ActionType.Tap || action is ActionType.LongTap)) {
            consecutiveFailures++
            AryaLogger.w(TAG, "Tap action produced no observable UI change.")
            return ReflectionRecord(
                verdict = ReflectionVerdict.WRONG_TARGET,
                nextStep = RecommendedNextStep.RETRY_WITH_JITTER,
                confidence = 0.80f,
                rationale = "Action executed successfully but produced zero observable screen change.",
                consecutiveFailures = consecutiveFailures
            )
        }

        // Check for expected text if specified
        if (!expectedEffect.isNullOrBlank()) {
            val hasExpectedText = currentState.nodes.any {
                it.text?.contains(expectedEffect, ignoreCase = true) == true ||
                it.contentDescription?.contains(expectedEffect, ignoreCase = true) == true
            }

            if (hasExpectedText) {
                consecutiveFailures = 0
                AryaLogger.i(TAG, "Expected effect '$expectedEffect' verified successfully on screen.")
                return ReflectionRecord(
                    verdict = ReflectionVerdict.SUCCESS,
                    nextStep = RecommendedNextStep.PROCEED,
                    confidence = 0.95f,
                    rationale = "Expected effect '$expectedEffect' verified on resulting screen.",
                    consecutiveFailures = 0
                )
            }
        }

        // Standard positive progress on significant or minor screen update
        if (diff.changeLevel == ChangeLevel.SIGNIFICANT || diff.changeLevel == ChangeLevel.MINOR) {
            consecutiveFailures = 0
            AryaLogger.i(TAG, "Action progressed screen: ${diff.summary}")
            return ReflectionRecord(
                verdict = ReflectionVerdict.SUCCESS,
                nextStep = RecommendedNextStep.PROCEED,
                confidence = 0.85f,
                rationale = "Screen progressed normally: ${diff.summary}",
                consecutiveFailures = 0
            )
        }

        // Default neutral reflection
        return ReflectionRecord(
            verdict = ReflectionVerdict.PARTIAL_SUCCESS,
            nextStep = RecommendedNextStep.PROCEED,
            confidence = 0.70f,
            rationale = "Action executed with neutral feedback.",
            consecutiveFailures = consecutiveFailures
        )
    }

    /**
     * Resets failure counters and hash tracking upon successful milestone transition.
     */
    fun reset() {
        recentHashes.clear()
        consecutiveFailures = 0
    }

    companion object {
        private const val TAG = "ReflectionEngine"
    }
}
