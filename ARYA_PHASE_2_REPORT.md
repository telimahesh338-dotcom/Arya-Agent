# ARYA — Phase 2: Accessibility Core Implementation Report

**Milestone:** Phase 2 — Accessibility Core  
**Date:** September 19, 2026  
**Status:** COMPLETE & VERIFIED  
**Repository:** `/storage/emulated/0/ARYA`

---

## 1. Executive Summary

Phase 2 builds the sensory and motor foundation of ARYA's on-device digital operator platform. In this phase, the **Accessibility Core** was engineered as a production-grade, modular subsystem that grants ARYA the ability to:
1. Detect and track the foreground active application, package, activity, and window hierarchy.
2. Interrogate, traverse, filter, and normalize Android's live `AccessibilityNodeInfo` tree into an immutable, detached `ScreenState` model.
3. Compute stable semantic identifiers and geometry bounds while safeguarding against stale/detached native node pointers and malformed vendor UI trees.
4. Detect meaningful UI changes between successive observations (`NONE`, `MINOR`, `SIGNIFICANT`) using SHA-256 structural hashing and actionable set differentials.
5. Execute **direct semantic actions** (tap, long-press, type text, scroll, swipe, back, home, launch app) targeting accessibility nodes natively without blindly defaulting to raw screen coordinates.
6. Enforce gesture timeouts, cancellation handling, and structured diagnostics with automatic secret/credential redaction.

---

## 2. Deliverables & Component Breakdown

### A. Perception Subsystem (`com.arya.perception`)

#### 1. `UiNode.kt`
- **Purpose:** Immutable, normalized snapshot of an individual accessibility UI element.
- **Detached Safety:** Fully detached from Android native OS node pointers to completely eliminate `StaleNodeException` and `IllegalStateException` during asynchronous reasoning loops.
- **Attributes:** Captures `id`, `nodeIndex`, `packageName`, `className`, `resourceId`, `text`, `contentDescription`, `boundsLeft`, `boundsTop`, `boundsRight`, `boundsBottom`, `isClickable`, `isLongClickable`, `isEditable`, `isScrollable`, `isEnabled`, `isVisibleToUser`, `isFocused`, `isSelected`, `isCheckable`, `isChecked`, `isPassword`, `depth`, `parentIndex`, and child indices.
- **Geometric & Semantic Utilities:** Provides `width`, `height`, `area`, `centerX`, `centerY`, `contains(x, y)`, `isActionable`, `semanticLabel`, and `toCompactString(legendIndex)`.

#### 2. `ScreenState.kt`
- **Purpose:** Normalized state representation of the screen at a single discrete moment.
- **Lazy Semantic Collections:** Exposes cached views for `actionableNodes`, `clickableNodes`, `editableNodes`, `scrollableNodes`, and `focusedNode`.
- **Structural Hashing (`structuralHash`):** Computes an 8-byte SHA-256 digest over the active package, dialog/keyboard states, and sorted actionable node attributes. Enables sub-millisecond change detection before invoking heavy vision/OCR pipelines.
- **Prompt Serialization (`toPromptRepresentation`):** Formats visible interactive UI elements into a token-efficient numbered legend format for LLM context injection.
- **Taproot Grounding & Snapping (`findGroundedNodeAt`):** Implements ARYA's taproot localization policy—locating the smallest containing interactive element or snapping within a 140px Euclidean radius.
- **Semantic Text Queries:** Supports `findNodesByText(query)` and `findFirstNodeByText(query)` for robust semantic targeting.

#### 3. `AccessibilityReader.kt`
- **Purpose:** Production-grade accessibility tree crawler.
- **Resilience & Bounds:** Implements bounded breadth-first traversal (max depth: 45) with cycle prevention via identity tracking to survive cyclic or malformed vendor view trees.
- **Window Interrogation:** Inspects `AccessibilityWindowInfo` to identify soft keyboards (`TYPE_INPUT_METHOD`) and modal dialogs (`TYPE_APPLICATION` with high layer elevation).
- **Stable ID Generation (`buildStableId`):** Generates deterministic semantic keys (`packageName:SimpleClassName#resourceName_label@index`) that persist across subtle layout shifts.
- **Safe Lifecycle Management:** Safely recycles traversed nodes on Android API < 34 and gracefully ignores recycled node exceptions.

#### 4. `ScreenChangeDetector.kt`
- **Purpose:** Post-action verification engine that compares pre- and post-action `ScreenState` snapshots.
- **Classification (`ChangeLevel`):**
  - `NONE`: Structural hash and package identical; no visible changes detected (flags retry/stuck condition).
  - `MINOR`: Localized UI updates or text updates ($\le 2$ elements added/removed).
  - `SIGNIFICANT`: Package changed, modal dialog appeared/dismissed, soft keyboard toggled, or major layout shift ($> 2$ elements added/removed).
- **Diagnostic Summary:** Formats a human-readable telemetry summary of observed state transitions.

---

### B. Action & Gesture Subsystem (`com.arya.actions`)

#### 1. `ActionType.kt`
- **Semantic Targeting Models:**
  - `ActionType.Tap`: Accepts `nodeId`, `targetText`, `legendId`, or fallback coordinates `(x, y)`. Computes `hasSemanticTarget`.
  - `ActionType.LongTap`: Accepts `nodeId`, `targetText`, duration, or fallback coordinates.
  - `ActionType.Scroll`: Accepts `direction` (`UP`, `DOWN`, `LEFT`, `RIGHT`), optional `nodeId`, and `distanceFraction`.
  - `ActionType.TypeText`: Accepts `text`, `nodeId`, `targetText`, `targetX`, `targetY`, and `pressEnter`.
  - `ActionType.Swipe`, `ActionType.PressBack`, `ActionType.PressHome`, `ActionType.LaunchApp`, `ActionType.Wait`.

#### 2. `GestureController.kt`
- **Execution Policy:** **Never blindly uses screen coordinates when an accessibility node can be targeted.**
- **Routing:**
  - For `Tap` / `LongTap`: Delegates to `AryaAccessibilityService.performClickOnNode` / `performLongClickOnNode`.
  - For `TypeText`: Targets the editable node directly via `performSetTextOnNode`, requesting focus if needed.
  - For `Scroll`: Targets scrollable containers natively via `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD`, falling back to smooth touch swiping.
  - For `Swipe` / `PressBack` / `PressHome` / `LaunchApp`: Dispatches physical gestures or Android global actions with calibrated settlement delays.

#### 3. `ActionEngine.kt`
- Integrates risk evaluation (`RiskClassifier`), approval pauses for high-risk actions, execution timing metrics, structured action traces (`ActionExecutionRecord`), and structured logging.

---

### C. Native Android Accessibility Service (`com.arya.android`)

#### `AryaAccessibilityService.kt`
- **Lifecycle & Connection Management:** Exposes reactive `isConnected` and `activePackage` StateFlows; registers with `CapabilityManager`.
- **State Capture:** Exposes `captureScreenState(): ScreenState` and `currentScreenState: StateFlow<ScreenState?>`.
- **Semantic Action Handlers:**
  - `performClickOnNode`: Searches node by stable ID or semantic text; walks ancestor chain to find clickable container; invokes `AccessibilityNodeInfo.ACTION_CLICK`; falls back to node bounds center coordinate tap.
  - `performLongClickOnNode`: Invokes `AccessibilityNodeInfo.ACTION_LONG_CLICK` or bounds center long-press.
  - `performSetTextOnNode`: Focuses target editable node and sets text via `AccessibilityNodeInfo.ACTION_SET_TEXT` with CharSequence bundle arguments.
  - `performScrollOnNode`: Executes native scroll actions before falling back to swipe gestures.
- **Hardware Touch Gestures:** Suspending `dispatchTap` and `dispatchSwipe` using `GestureDescription.Builder`.
- **Timeout & Cancellation:** `dispatchGestureSuspending` enforces `withTimeoutOrNull(4000L)` with coroutine cancellation handlers.
- **Configuration (`accessibility_service_config.xml`):**
  - Event types: `typeWindowStateChanged`, `typeWindowContentChanged`, `typeViewClicked`, `typeViewFocused`, `typeViewScrolled`, `typeWindowsChanged`.
  - Flags: `flagRetrieveInteractiveWindows`, `flagReportViewIds`, `flagIncludeNotImportantViews`, `flagRequestTouchExplorationMode`.
  - Capabilities: `canRetrieveWindowContent="true"`, `canPerformGestures="true"`.

---

### D. Diagnostics & Security (`com.arya.diagnostics`)

#### `AryaLogger.kt`
- **Structured Logging:** Centralized logger emitting to Android `Log` and an in-memory `SharedFlow<LogEntry>`.
- **Automated PII & Secret Redaction:** Automatically sanitizes log strings using high-precision regex masks for:
  - OpenAI API keys (`sk-...`)
  - Google Gemini API keys (`AIza...`)
  - Anthropic API keys (`anthropic-...`)
  - Bearer tokens (`Bearer ...`)
  - Passwords, PINs, tokens (`password=...`, `secret=...`)
  - Credit card numbers (`\b(?:\d{4}[ -]?){3}\d{4}\b`)

---

## 3. Verification & Validation Results

### A. Static Architecture & Integrity Suite
A complete 7-point validation suite was executed over the codebase:

| Check # | Verification Item | Status | Notes |
| :---: | :--- | :---: | :--- |
| **01** | Kotlin package & folder path alignment | **PASSED** | 26 source and test files verified across `app/src/main` and `app/src/test`. |
| **02** | Phase 2 component presence | **PASSED** | All 9 core files present in expected packages. |
| **03** | AryaAccessibilityService API completeness | **PASSED** | Verified all 12 semantic and gesture methods. |
| **04** | GestureController semantic targeting policy | **PASSED** | Verified non-blind invocation of `performClickOnNode`, `performLongClickOnNode`, `performSetTextOnNode`, `performScrollOnNode`. |
| **05** | Accessibility XML configuration | **PASSED** | Verified flags, gestures, content retrieval, and window change event listeners. |
| **06** | Secret sanitization regex accuracy | **PASSED** | Verified redaction of OpenAI, Gemini, password, and credit card patterns. |
| **07** | Taproot grounding & snapping algorithms | **PASSED** | Exact container match, nearest-neighbor 140px radius snap, and fallback behavior verified. |

### B. Unit Test Suite Implemented
- `ScreenStateTest.kt`: Validates geometry computations, actionable filters, structural hash stability, prompt representation, and grounding snapping.
- `ScreenChangeDetectorTest.kt`: Validates initial observations, no-change detection, package switches, dialog triggers, and keyboard toggles.
- `AryaLoggerTest.kt`: Validates redaction of all secret formats while preserving non-sensitive operational logs.
- `ActionTypeTest.kt`: Validates semantic target detection for Tap, LongTap, and TypeText actions.

---

## 4. Next Phase Readiness

Phase 2 is fully implemented, verified, and integrated into the ARYA architecture. The project is ready to proceed to **Phase 3: Screen Perception Engine** (`ScreenCaptureService` via MediaProjection with dynamic 50% scaling and aspect preservation).
