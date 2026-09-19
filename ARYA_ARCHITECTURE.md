# ARYA — Master System Architecture & Engineering Specification

**Project:** ARYA (Autonomous Reactive Yielding Android Agent)  
**Document Version:** 1.0.0-PROD  
**Target Platform:** Native Android 8.0+ (API 26–35)  
**Primary Language:** Kotlin 2.0+ (Coroutine / Flow / Compose Architecture)  
**Execution Paradigm:** Pure On-Device Local Core with Direct Multi-Provider API Dispatch

---

## 1. Architectural Blueprint & Executive Overview

ARYA is architected as an autonomous digital operator that runs directly on an Android device. It translates natural user intent into reliable GUI interactions across any application without requiring proprietary app APIs, rooted firmware, or a mandatory cloud backend.

```
                             👤 HUMAN OPERATOR
                                     │
                             Voice / Text / UI
                                     │
                                     ▼
                     ┌───────────────────────────────┐
                     │       ARYA COGNITIVE CORE     │
                     │  ┌─────────────────────────┐  │
                     │  │      AgentEngine        │  │
                     │  │   (Task Orchestrator)   │  │
                     │  └────────────┬────────────┘  │
                     └───────────────┼───────────────┘
                                     │
           ┌─────────────────────────┼─────────────────────────┐
           ▼                         ▼                         ▼
  ┌─────────────────┐      ┌───────────────────┐     ┌───────────────────┐
  │   PLANNER &     │      │   MEMORY ENGINE   │     │  SOCIAL & EMOTION │
  │   TASK MANAGER  │      │ (ASM & Room DB)   │     │    MODEL (SEAM)   │
  │ • Decomposition │      │ • Working Memory  │     │ • Tone Adaptation │
  │ • Milestone Map │      │ • Episodic / ASM  │     │ • Emotion Context │
  │ • Risk Assessor │      │ • Memory Gate     │     │ • Urgency Factor  │
  └────────┬────────┘      └─────────┬─────────┘     └─────────┬─────────┘
           │                         │                         │
           └─────────────────────────┼─────────────────────────┘
                                     ▼
                     ┌───────────────────────────────┐
                     │          MODEL ROUTER         │
                     │  ┌─────────────────────────┐  │
                     │  │  Tiered Dynamic Engine  │  │
                     │  └────────────┬────────────┘  │
                     └───────────────┼───────────────┘
                                     │
          ┌──────────────────┬───────┴──────────┬──────────────────┐
          ▼                  ▼                  ▼                  ▼
     Google Gemini      Anthropic          OpenAI / VLLM       Local Models
    (3.7 / 2.5 Flash)   (Claude 3.7)       (GPT-4o / UI-TARS) (ONNX/ML Kit)
          │                  │                  │                  │
          └──────────────────┴───────┬──────────┴──────────────────┘
                                     ▼
                     ┌───────────────────────────────┐
                     │       PERCEPTION ENGINE       │
                     │  ┌─────────────────────────┐  │
                     │  │ Escalation & Fusion     │  │
                     │  └────────────┬────────────┘  │
                     └───────────────┼───────────────┘
                                     │
          ┌──────────────────────────┼──────────────────────────┐
          ▼                          ▼                          ▼
  Native Accessibility        On-Device ML Kit         Hardware MediaProjection
  (UI Hierarchy & Nodes)     (Offline Rapid OCR)       (50% Scaled Screenshot)
          │                          │                          │
          └──────────────────────────┼──────────────────────────┘
                                     ▼
                     ┌───────────────────────────────┐
                     │       GROUNDING ENGINE        │
                     │ • Bounding Box Occlusion Filter│
                     │ • Legend ID Badging (1..N)    │
                     │ • Accessibility Snapping      │
                     │ • Center-Point Coordinate Math│
                     └───────────────┬───────────────┘
                                     ▼
                                ScreenState
                                     ▼
                     ┌───────────────────────────────┐
                     │         ACTION ENGINE         │
                     │  Tap • Swipe • Type • Scroll  │
                     │  Back • Home • Launch • Wait  │
                     └───────────────┬───────────────┘
                                     ▼
                     ┌───────────────────────────────┐
                     │         VERIFY ENGINE         │
                     │ • Keypoint Visual Consensus   │
                     │ • Text & Activity Predicates  │
                     │ • Pre/Post Screen dHash Diff  │
                     └───────────────┬───────────────┘
                                     │
                         ┌───────────┴───────────┐
                         ▼                       ▼
                   [ SUCCESS ]              [ FAILURE ]
                         │                       │
                         ▼                       ▼
                  Advance Subgoal          REFLECT & REPLAN
                                                 │
                                                 ▼
                                          RECOVERY ROUTINE
                                          • Dismiss Dialogs
                                          • Backstep & Retry
                                          • Stuck Loop Break
                                          • Human Escalation
```

---

## 2. Core Subsystems Specification

### 2.1. Perception Engine & Tri-Tier Escalation Strategy

Blindly streaming high-resolution bitmaps to external vision LLMs creates prohibitive latency (3–8 seconds) and excessive token costs. ARYA deploys a **Tri-Tier Perception Escalation Strategy**:

```
                       Input Screen Request
                                │
                                ▼
               Is Accessibility Hierarchy Sufficient?
               (Valid clickable nodes, clear text/labels,
                unoccluded target elements present)
                                │
                 ┌──────────────┴──────────────┐
                 ▼ YES                         ▼ NO
          Use Accessibility             Is On-Device OCR Sufficient?
          Tree Representation          (Recognizable text buttons,
          (Latency: ~15ms)              known textual targets)
                                               │
                                 ┌─────────────┴─────────────┐
                                 ▼ YES                       ▼ NO
                          Use ML Kit Local OCR        Escalate to Full Vision
                          (Latency: ~60ms)            Multimodal Foundation Model
                                                      (Latency: ~1200ms)
```

#### Perception Component Responsibilities
1. **Accessibility Reader (`AccessibilityReader`):**
   - Interrogates `AccessibilityNodeInfo` tree recursively.
   - Extracts: `boundsInScreen`, `className`, `text`, `contentDescription`, `viewIdResourceName`, `isClickable`, `isScrollable`, `isEditable`, and `packageName`.
   - Filters non-actionable layout nodes and calculates visual occlusions using spatial intersection testing.
2. **On-Device OCR (`OcrEngine`):**
   - Employs Google ML Kit Text Recognition running purely on-device (CPU/NPU).
   - Extracts bounding boxes and text strings for canvas-based apps (Flutter, Unity, custom webviews) where accessibility nodes are unexposed.
3. **Screen Capture (`ScreenCaptureService`):**
   - Runs as a persistent foreground service with `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
   - Captures frames via `ImageReader` at a normalized 50% device resolution to maintain sub-100ms capture latency.
   - Preserves exact aspect ratio and provides real-to-capture coordinate mapping multipliers (`scaleX`, `scaleY`).

---

### 2.2. Grounding Engine & Target Localization

To prevent misclicks and spatial hallucinations, ARYA never executes a raw model-predicted coordinate without validation. Action targeting follows a strict 6-layer priority:

```
Priority 1: Direct Accessibility Node Match (ID / Exact Text / Content Description)
Priority 2: Semantic UI Element (Normalized Levenshtein Match on UI Tree)
Priority 3: Legend ID Badge (Numbered element assignment via MarkAssigner)
Priority 4: On-Device OCR Grounded Center Point
Priority 5: Vision Grounding Coordinate Snapping (taproot minimum-area snap)
Priority 6: Normalized Model Coordinate (Last resort, bounded to viewport)
```

#### Taproot Snapping Implementation (Kotlin Specification)
When a coordinate $(P_x, P_y)$ is predicted by a vision model:
1. Find all clickable accessibility nodes whose bounding boxes contain $(P_x, P_y)$.
2. If containing nodes exist, select the element with the **smallest bounding area** (the most granular, specific child target) and return its true geometrical center:
   $$C_x = \frac{x_1 + x_2}{2}, \quad C_y = \frac{y_1 + y_2}{2}$$
3. If no element directly contains the point, query all clickable elements within Euclidean radius threshold $R \le 140\text{px}$. If found, snap to the nearest center.
4. If no element satisfies the threshold, emit the scaled coordinate directly with an ungrounded audit warning.

---

### 2.3. The 9-Stage Action Loop

Every task step is executed through an unyielding 9-stage operational loop:

$$\text{OBSERVE} \to \text{UNDERSTAND} \to \text{PLAN} \to \text{ACT} \to \text{OBSERVE AGAIN} \to \text{COMPARE} \to \text{VERIFY} \to \text{REFLECT} \to \text{REPLAN / PROCEED}$$

```kotlin
sealed interface ActionLoopResult {
    data class NextStep(val nextSubgoal: String) : ActionLoopResult
    data class Recover(val reason: RecoveryReason, val strategy: RecoveryStrategy) : ActionLoopResult
    data class AwaitHuman(val riskLevel: RiskLevel, val prompt: String) : ActionLoopResult
    data class Completed(val summary: String) : ActionLoopResult
    data class Terminated(val failureDetail: String) : ActionLoopResult
}
```

1. **Observe:** Gather accessibility hierarchy, active package name, and frame bitmap.
2. **Understand:** Construct `ScreenState`, identifying active inputs, keyboard state, dialogs, and interactive targets.
3. **Plan:** Evaluate remaining goal milestones against `ScreenState`; select next primitive action and formulate an `ExpectedEffect`.
4. **Act:** Check risk rating; if safe, dispatch gesture via `AccessibilityService.dispatchGesture`.
5. **Observe Again:** Wait for UI settle time ($700\text{ms}$–$1200\text{ms}$); capture subsequent `ScreenState`.
6. **Compare:** Calculate difference metrics:
   - Visual Difference: 64-bit perceptual hash (`dHash`) Hamming distance.
   - Structural Difference: Accessibility node count, focused node change, active package.
7. **Verify:** Check deterministic assertions (expected text presence, activity transition, target element dismissal).
8. **Reflect:** Evaluate:
   $$\text{PreviousState} + \text{Action} + \text{CurrentState} + \text{ExpectedEffect} \implies \text{OutcomeEvaluation}$$
9. **Replan / Proceed:** If outcome matches expectation, advance working memory; if state is unchanged or erroneous, trigger recovery branch.

---

### 2.4. Memory Engine: Anchored State Memory (ASM) & Selective Filtering

ARYA adheres to the core doctrine: **Understand everything. Remember only what matters.**

```
                            Conversation / Action Event
                                         │
                                         ▼
                            Candidate Memory Extractor
                                         │
                                         ▼
                     ┌───────────────────────────────────────┐
                     │          MEMORY GATE FILTER           │
                     │                                       │
                     │  • Is it user-specific?               │
                     │  • Is it reusable across sessions?    │
                     │  • Is it explicitly requested?        │
                     │  • Is it temporary task context?      │
                     │  • Is it sensitive / credential?      │
                     │  • Confidence Score > Threshold?      │
                     └───────────────────┬───────────────────┘
                                         │
                         ┌───────────────┼───────────────┐
                         ▼               ▼               ▼
                   [ SCORE < 0.4 ] [ SCORE 0.4-0.7 ] [ SCORE > 0.7 ]
                         │               │               │
                         ▼               ▼               ▼
                      DISCARD        TEMPORARY       LONG-TERM
                   (Zero storage)  (Working Task)    PERSISTENCE
                                   (Auto-expires)  (Anchored in Room)
```

#### Memory Classifications
1. **Working Memory (`WorkingMemory`):**
   - Ephemeral in-memory state of the active task.
   - Implements **Anchored State Memory (ASM)** (derived from AndroTMem): stores critical intermediate anchors (e.g. tracking numbers, selected dates, cart totals, flight numbers) with causal link graphs. Cleared upon task completion.
2. **Episodic Memory (`EpisodicMemory`):**
   - High-level summaries of completed tasks, encountered errors, and effective workflows.
3. **App Memory (`AppMemory`):**
   - Reusable procedural knowledge about specific applications (e.g. `"In App X, search button requires double-tap"`, `"Login button is obscured by floating banner"`).
4. **Entity / People Memory (`EntityMemory`):**
   - Important facts regarding contacts, family members, or referenced entities explicitly confirmed by the user.
5. **Preference Memory (`PreferenceMemory`):**
   - User interaction preferences (e.g. language preference, default navigation app, communication tone).

#### Memory Decay & Governance Attributes
Every stored long-term memory entity contains:
- `id: UUID`
- `content: String`
- `category: MemoryCategory`
- `importanceScore: Float` (0.0 to 1.0)
- `confidenceScore: Float` (0.0 to 1.0)
- `usageCount: Int`
- `createdAt: Long`
- `lastUsedAt: Long`
- `expiresAt: Long?` (null for permanent)
- `userConfirmed: Boolean`
- `source: MemorySource` (`USER_EXPLICIT`, `INFERRED_INTERACTION`, `VERIFIED_RESULT`)

External information fetched from search engines or LLM completions is marked `EXTERNAL_EPHEMERAL` and discarded immediately upon task termination unless explicit user confirmation is given.

---

### 2.5. Social & Emotional Awareness Model (SEAM)

ARYA does not feign human biological consciousness. Instead, it tracks an explicit, debuggable **EmotionalContext** to adapt conversational pacing, explanation depth, and system tone.

```kotlin
data class CognitiveState(
    val highLevelGoal: String,
    val currentSubgoal: String,
    val activeMilestoneIndex: Int,
    val totalMilestones: Int,
    val attentionFocus: String,
    val confidenceLevel: Float,      // 0.0 to 1.0
    val perceivedRisk: RiskLevel,
    val consecutiveFailures: Int,
    val isStuck: Boolean
)

data class SocialState(
    val detectedEmotion: UserEmotion, // FRUSTRATED, CONFUSED, URGENT, SATISFIED, NEUTRAL
    val communicationStyle: CommStyle, // CONCISE, DETAILED, TECHNICAL, CASUAL
    val userTechnicalAptitude: AptitudeLevel,
    val requiresReassurance: Boolean
)
```

#### Dynamic Behavior Adaptation
- **When `UserEmotion.FRUSTRATED`:** Suppress verbose step explanations. Reduce confirmation overhead for trivial actions. Immediately provide concise status updates. If recovery fails twice, escalate directly to human handoff with clear diagnostic summary.
- **When `UserEmotion.URGENT`:** Maximize action execution speed; set settling timeouts to optimal minimums; eliminate pleasantries.
- **When `UserEmotion.CONFUSED`:** Provide clear, comforting, high-level milestone explanations; highlight active screen areas on overlay.

---

### 2.6. Model Router & Secure Key Vault

To guarantee fault tolerance against rate limits, service outages, and quota exhaustion, ARYA uses a dynamic multi-provider routing layer.

```
                                Task Action Request
                                         │
                                         ▼
                               What is the task type?
                                         │
           ┌─────────────────────────────┼─────────────────────────────┐
           ▼                             ▼                             ▼
    Simple UI Gesture             Complex Workflow             Visual Grounding /
    & Form Filling                & Causal Planning            Dense UI Parsing
           │                             │                             │
           ▼                             ▼                             ▼
     Fast / Cost-Effective         Heavy Reasoning              Native Vision GUI
     Tier 1: Gemini 2.5 Flash      Tier 1: Claude 3.7 Sonnet    Tier 1: Gemini 3.7 Flash
     Tier 2: GPT-4o-mini           Tier 2: GPT-4o               Tier 2: UI-TARS / Qwen-VL
```

#### Key Vault Architecture
- **Hardware-Backed Security:** API keys are encrypted using AES-256-GCM via the Android `MasterKey` inside the hardware **Android Keystore**.
- **Rotation & Health Tracker:**
  - Maintains per-provider key pools with round-robin or priority selection.
  - Intercepts HTTP `429 Too Many Requests` and `503 Service Unavailable`.
  - Automatically isolates a failing key, applies exponential backoff, and switches to the secondary key or alternate provider within $200\text{ms}$.
  - Strictly zeroes out key bytes from memory and never writes plaintext keys to logs or traces.

---

### 2.7. Safety, Human Oversight & Risk Engine

ARYA categorizes every planned action before execution using a 4-tier risk classification:

| Risk Level | Trigger Scenarios | System Behavior |
| :--- | :--- | :--- |
| **Low** | Read-only actions, navigation, scrolling, opening public apps, search | Immediate automatic execution. |
| **Medium** | Composing draft messages, adding calendar events, changing non-critical app settings | Automatic execution with live overlay notification; interruptible by human touch or voice. |
| **High** | Submitting completed forms, making account modifications, booking appointments | Mandatory modal confirmation on overlay; requires explicit user approval. |
| **Critical** | Financial transactions, payments, biometric approval, credential entry, deleting data | Hard pause. ARYA releases screen control and requests human operator takeover. |

#### Emergency Stop Mechanisms
1. **Always-On Floating Stop Button:** High-priority overlay widget listening for immediate touch interrupt.
2. **Voice Interrupt:** Audio engine listens for `"Stop"`, `"Cancel"`, or `"Pause ARYA"` during speech recognition sessions.
3. **Physical Volume Rocker Hook:** Pressing both volume buttons simultaneously triggers an immediate accessibility gesture kill-switch.

---

### 2.8. Self-Recovery & Anti-Stuck Loop System

```
                         Action Execution Completed
                                     │
                                     ▼
                        Screen Change Classification
                                     │
                 ┌───────────────────┼───────────────────┐
                 ▼                   ▼                   ▼
               NONE                MINOR            SIGNIFICANT
          (Screen static)     (Spinner/loading)  (Target state reached)
                 │                   │                   │
                 ▼                   ▼                   ▼
          Increment Retry     Wait Loading Settle     Reset Retry Counter
          Counter (Max 3)     (1800ms); Do not tap    Proceed to Next Step
                 │
                 ▼
          Has Retry Count Exceeded Limit?
                 │
           ┌─────┴─────┐
           ▼ YES       ▼ NO
     Trigger Recovery  Retap with Spatial Jitter (+/- 15px)
           │
           ▼
     Check Perceptual Difference Hash (dHash) History
     • If last 4 screens Hamming Distance < 10:
       1. Auto-dismiss potential system permission dialogs.
       2. If keyboard is covering elements, dispatch Back to collapse IME.
       3. If modal popup detected, tap outside or tap Dismiss.
       4. If still stuck, execute single Back navigation to restore prior state.
       5. If all automated strategies fail, invoke Human Assistance Dialog.
```

---

## 3. Native Android Project Structure

The project is structured under standard Android Gradle conventions:

```
/storage/emulated/0/ARYA/
├── ARYA_REPOSITORY_AUDIT.md
├── ARYA_ARCHITECTURE.md
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── res/
│           │   ├── xml/accessibility_service_config.xml
│           │   └── values/strings.xml
│           └── java/com/arya/
│               ├── AryaApplication.kt
│               │
│               ├── agent/
│               │   ├── AgentEngine.kt
│               │   ├── TaskManager.kt
│               │   ├── Planner.kt
│               │   ├── Executor.kt
│               │   ├── Reflector.kt
│               │   ├── Replanner.kt
│               │   └── RiskClassifier.kt
│               │
│               ├── perception/
│               │   ├── PerceptionFusion.kt
│               │   ├── AccessibilityReader.kt
│               │   ├── ScreenCaptureService.kt
│               │   ├── OcrEngine.kt
│               │   ├── GroundingEngine.kt
│               │   ├── ScreenChangeDetector.kt
│               │   └── ScreenState.kt
│               │
│               ├── actions/
│               │   ├── ActionEngine.kt
│               │   ├── GestureController.kt
│               │   ├── ActionType.kt
│               │   └── KeyCodeMapper.kt
│               │
│               ├── memory/
│               │   ├── MemoryEngine.kt
│               │   ├── WorkingMemory.kt
│               │   ├── AnchoredStateMemory.kt
│               │   ├── MemoryGate.kt
│               │   ├── MemoryDecayScheduler.kt
│               │   ├── db/
│               │   │   ├── AryaDatabase.kt
│               │   │   ├── MemoryDao.kt
│               │   │   └── MemoryEntities.kt
│               │   └── models/
│               │       └── MemoryRecord.kt
│               │
│               ├── models/
│               │   ├── ModelRouter.kt
│               │   ├── ProviderClient.kt
│               │   ├── gemini/GeminiProvider.kt
│               │   ├── claude/ClaudeProvider.kt
│               │   ├── openai/OpenAiProvider.kt
│               │   └── local/LocalModelProvider.kt
│               │
│               ├── social/
│               │   ├── EmotionalContextDetector.kt
│               │   ├── SocialStateManager.kt
│               │   └── AdaptiveResponseFormatter.kt
│               │
│               ├── android/
│               │   ├── AryaAccessibilityService.kt
│               │   ├── CapabilityManager.kt
│               │   ├── ShizukuBridge.kt
│               │   └── NotificationListener.kt
│               │
│               ├── voice/
│               │   ├── VoiceInteractionService.kt
│               │   ├── SpeechRecognitionHandler.kt
│               │   ├── TextToSpeechEngine.kt
│               │   └── WakeWordDetector.kt
│               │
│               ├── security/
│               │   ├── KeyVault.kt
│               │   ├── BiometricAuthenticator.kt
│               │   └── AuditLogger.kt
│               │
│               ├── ui/
│               │   ├── MainActivity.kt
│               │   ├── overlay/AgentFloatingOverlay.kt
│               │   ├── console/AgentConsoleScreen.kt
│               │   ├── memory/MemoryViewerScreen.kt
│               │   ├── settings/ProviderSettingsScreen.kt
│               │   └── theme/
│               │
│               └── diagnostics/
│                   ├── BenchmarkRunner.kt
│                   ├── TrajectoryRecorder.kt
│                   └── TelemetryCollector.kt
```

---

## 4. Build, Gradle & Dependency Plan

### 4.1. Core Gradle Build Configuration (`app/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.arya"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arya.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

### 4.2. Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
coreKtx = "1.15.0"
coroutines = "1.9.0"
lifecycle = "2.8.7"
composeBom = "2024.10.01"
activityCompose = "1.9.3"
room = "2.6.1"
ktor = "3.0.1"
serialization = "1.7.3"
securityCrypto = "1.1.0-alpha06"
mlkitText = "16.0.1"
ksp = "2.0.21-1.0.28"
shizuku = "13.1.5"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }

# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }

# Networking (Ktor 3.0 Native HTTP Client)
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Persistence (Room Database for ASM & Long-Term Memory)
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Security (Android KeyStore & EncryptedSharedPreferences)
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }

# ML Kit (On-Device Local OCR)
mlkit-text-recognition = { group = "com.google.android.gms", name = "play-services-mlkit-text-recognition", version.ref = "mlkitText" }

# Optional Privileged Access (Shizuku)
shizuku-api = { group = "dev.rikka.shizuku", name = "api", version.ref = "shizuku" }
shizuku-provider = { group = "dev.rikka.shizuku", name = "provider", version.ref = "shizuku" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## 5. Phased Implementation Roadmap

ARYA will be constructed through an orderly, incremental 20-phase engineering execution plan:

| Phase | Milestone | Objective & Deliverables |
| :---: | :--- | :--- |
| **01** | **Foundation & Architecture Shell** | Initialize Gradle project, Compose app scaffold, navigation, permissions, and build verification. |
| **02** | **Accessibility Core** | Implement `AryaAccessibilityService`, node tree extraction, bounds resolution, and input event hooks. |
| **03** | **Screen Perception Engine** | Implement `ScreenCaptureService` via MediaProjection with dynamic 50% scaling and aspect preservation. |
| **04** | **ScreenState & Fusion** | Build unified `ScreenState` model aggregating accessibility nodes, OCR blocks, and viewport metadata. |
| **05** | **Action Engine & Gestures** | Implement asynchronous `GestureController` (tap, long press, swipe, scroll, type, back, home, app launch). |
| **06** | **Single-Model Agent Core** | Connect single VLM client (Gemini 2.5 Flash) to basic perception and execution loop. |
| **07** | **Observe $\to$ Act $\to$ Verify Loop** | Introduce `taproot` grounding snapping, permission auto-clearing, and perceptual dHash change detection. |
| **08** | **Autonomous Planner** | Implement goal decomposition, subgoal milestone tracking, and task state management. |
| **09** | **Hierarchical Reflector** | Implement two-tier reflection (action-level registration + milestone progress verification). |
| **10** | **Anti-Stuck & Self-Recovery** | Implement stuck loop detection, backstep navigation, retry jitter, and graceful failure escalation. |
| **11** | **Memory Gate & Scoring** | Implement memory extraction pipeline, importance scoring filter, and discarding engine. |
| **12** | **Long-Term Memory Engine** | Deploy Room Database schema for Anchored State Memory (ASM), App Memory, Entity Memory, and Decay. |
| **13** | **Multi-Model Router** | Build provider abstraction layer routing across Gemini, Claude, OpenAI, and custom endpoints. |
| **14** | **Secure Key Vault** | Implement Android Keystore hardware encryption, key pool rotation, and 429 quota backoff. |
| **15** | **Voice Pipeline** | Implement push-to-talk, `SpeechRecognizer`, `TextToSpeech`, and audio interruption handling. |
| **16** | **Social & Emotional Context** | Build `EmotionalContextDetector` and `AdaptiveResponseFormatter` for tone and brevity adaptation. |
| **17** | **Safety & Risk Approval** | Build 4-tier risk classifier, floating approval modals, and emergency stop button widget. |
| **18** | **Optional Privileged Shizuku** | Implement optional Shizuku shell bridge for fast app-kill and background task acceleration without root. |
| **19** | **Benchmark Suite & Diagnostics** | Implement 18 automated validation tests across navigation, web forms, error recovery, and memory. |
| **20** | **Hardening & Release** | ProGuard optimization, memory leak profiling, security audit, and release APK assembly. |

---

## 6. Verification & Evaluation Suite (18 Core Test Scenarios)

Before declaring production readiness, ARYA must achieve $\ge 90\%$ pass rate across 18 benchmark scenarios:

1. **Unknown App Exploration:** Open an unindexed calculator or tool app and successfully perform an operation.
2. **Web Browser Navigation:** Launch Chrome, open a target domain, accept cookie banner, and extract target article heading.
3. **Multi-Step Form Filling:** Locate registration screen, fill text fields, select dropdown, and halt before submit.
4. **Keyword & List Search:** Open settings, search for `"Private DNS"`, locate item, and tap into target submenu.
5. **Continuous Scrolling:** Scroll down a dynamic feed until an item matching specific text criteria is located and centered.
6. **System Permission Interception:** Automatically detect and dismiss `"Allow location access"` modal without failing parent task.
7. **IME Keyboard Collision Handling:** Dismiss on-screen soft keyboard when it occludes an actionable confirmation button.
8. **Wrong Tap Recovery:** Intentionally tap a dead area; verify change detector flags `NONE`, retries with jitter, and recovers.
9. **Stuck Loop Resolution:** Force a 4-step static screen loop; verify `stuckHint` is generated and backstep navigation succeeds.
10. **Model Outage Failover:** Simulate HTTP 500 on primary model; verify router switches to secondary provider in $<300\text{ms}$.
11. **API Key Quota Exhaustion:** Inject HTTP 429 on Key A; verify Key Vault rotates to Key B seamlessly.
12. **Network Dropout Handling:** Disconnect WiFi mid-task; verify agent enters graceful pause with user status notification.
13. **Long-Horizon Workflow (30+ steps):** Execute multi-app comparison task maintaining state anchors across apps without drift.
14. **Memory Retrieval Verification:** Store user preference `"Always use DuckDuckGo"`; verify subsequent web searches honor preference.
15. **Memory Gate Rejection:** Feed transient statement `"I had tea today"`; verify memory gate assigns score $<0.3$ and discards.
16. **Temporary Memory Expiration:** Store meeting time `"Meeting at 4pm"`; verify entity expires and purges from active context.
17. **Human Confirmation Guardrail:** Attempt a simulated checkout action; verify hard stop modal appears and awaits user biometric/PIN.
18. **Emergency Kill-Switch:** Trigger floating overlay stop button during active swipe; verify instant gesture abort and cleanup.

---

*This architecture document constitutes the sole authoritative blueprint for ARYA development.*
