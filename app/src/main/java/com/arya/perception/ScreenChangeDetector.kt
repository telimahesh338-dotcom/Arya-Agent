package com.arya.perception

import kotlinx.serialization.Serializable

enum class ChangeLevel {
    NONE,
    MINOR,
    SIGNIFICANT
}

@Serializable
data class ScreenDifference(
    val changeLevel: ChangeLevel,
    val packageChanged: Boolean,
    val dialogAppeared: Boolean,
    val dialogDismissed: Boolean,
    val keyboardToggled: Boolean,
    val addedNodeCount: Int,
    val removedNodeCount: Int,
    val summary: String
)

/**
 * Compares two consecutive ScreenState snapshots to classify the effect of an action.
 */
class ScreenChangeDetector {

    fun detectChange(before: ScreenState?, after: ScreenState): ScreenDifference {
        if (before == null) {
            return ScreenDifference(
                changeLevel = ChangeLevel.SIGNIFICANT,
                packageChanged = true,
                dialogAppeared = after.isDialogShowing,
                dialogDismissed = false,
                keyboardToggled = after.isKeyboardVisible,
                addedNodeCount = after.actionableNodes.size,
                removedNodeCount = 0,
                summary = "Initial screen observation (${after.packageName})"
            )
        }

        val packageChanged = before.packageName != after.packageName
        val dialogAppeared = !before.isDialogShowing && after.isDialogShowing
        val dialogDismissed = before.isDialogShowing && !after.isDialogShowing
        val keyboardToggled = before.isKeyboardVisible != after.isKeyboardVisible

        // Compare structural hashes
        if (before.structuralHash == after.structuralHash && !packageChanged && !keyboardToggled) {
            return ScreenDifference(
                changeLevel = ChangeLevel.NONE,
                packageChanged = false,
                dialogAppeared = false,
                dialogDismissed = false,
                keyboardToggled = false,
                addedNodeCount = 0,
                removedNodeCount = 0,
                summary = "No visible screen change detected"
            )
        }

        // Fast node set difference
        val beforeActionable = before.actionableNodes.map { it.semanticLabel to it.className }.toSet()
        val afterActionable = after.actionableNodes.map { it.semanticLabel to it.className }.toSet()

        val added = afterActionable - beforeActionable
        val removed = beforeActionable - afterActionable

        val changeLevel = when {
            packageChanged || dialogAppeared || dialogDismissed -> ChangeLevel.SIGNIFICANT
            keyboardToggled -> ChangeLevel.SIGNIFICANT
            added.size > 2 || removed.size > 2 -> ChangeLevel.SIGNIFICANT
            added.isNotEmpty() || removed.isNotEmpty() -> ChangeLevel.MINOR
            else -> ChangeLevel.MINOR
        }

        val summary = buildString {
            if (packageChanged) append("Switched to package: ${after.packageName}. ")
            if (dialogAppeared) append("Modal dialog appeared. ")
            if (dialogDismissed) append("Dialog dismissed. ")
            if (keyboardToggled) append("Keyboard toggled (${if (after.isKeyboardVisible) "shown" else "hidden"}). ")
            if (added.isNotEmpty()) append("+${added.size} elements. ")
            if (removed.isNotEmpty()) append("-${removed.size} elements.")
            if (isEmpty()) append("Minor UI update (${changeLevel.name})")
        }

        return ScreenDifference(
            changeLevel = changeLevel,
            packageChanged = packageChanged,
            dialogAppeared = dialogAppeared,
            dialogDismissed = dialogDismissed,
            keyboardToggled = keyboardToggled,
            addedNodeCount = added.size,
            removedNodeCount = removed.size,
            summary = summary.trim()
        )
    }
}
