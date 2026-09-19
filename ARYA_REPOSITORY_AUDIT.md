# ARYA — Comprehensive Repository Audit & Technical Due Diligence

**Project:** ARYA — Autonomous Multimodal Android AI Agent  
**Date:** September 2026  
**Auditor:** Antigravity AI Engineering Core  
**Target Platform:** Native Android (Kotlin / Jetpack Compose / On-Device Core)

---

## 1. Executive Summary & Audit Methodology

To design and build **ARYA** as an elite digital operator for Android—adhering strictly to the cognitive paradigm:
$$\text{SEE} \to \text{UNDERSTAND} \to \text{THINK} \to \text{PLAN} \to \text{ACT} \to \text{VERIFY} \to \text{REMEMBER USEFULLY} \to \text{REFLECT} \to \text{REPLAN}$$

we conducted an exhaustive technical, architectural, and legal audit across the primary research pool and related state-of-the-art GUI-agent ecosystems.

### Strict Governance Rules Applied
1. **Zero Blind Copying:** No code is adopted without analyzing its AST, lifecycle compatibility, threading model, and security posture.
2. **License Isolation & Copyleft Sanitization:** Any repository bearing restrictive or copyleft licenses (notably **GNU AGPL v3.0** found in `AnsonLai/Android-Use-Agent`) is barred from direct code inclusion. All required behaviors are implemented independently from first principles.
3. **Native On-Device Feasibility:** Heavy, fragile desktop- or ADB-bound architectures (such as bundled Node.js runtimes or Python/PyTorch server daemons) are systematically re-engineered into native, efficient Android Kotlin components running directly on the OS.
4. **Reliability First:** Elimination of pure-coordinate guessing in favor of a 6-layer grounding hierarchy (Accessibility Node $\to$ Semantic UI $\to$ Text/Content Description $\to$ On-Device OCR $\to$ Vision Grounding $\to$ Coordinate Fallback).

---

## 2. Core Repository Matrix

| Repository | Primary Capability | Architecture & Stack | License | Reuse? | Reference Only? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **adev0x/android-agent** | On-device perception loop, screen capture, accessibility gestures | Native Android (Kotlin, MediaProjection, AccessibilityService) | **MIT** | **Yes** (Direct architectural reuse of capture & dHash) | No |
| **GiggleWang/MobileAgent-Android** | Multi-role execution (Manager, Executor, Reflector, Notetaker) | Native Android (Kotlin, coroutines, Retrofit) | **MIT** | **Yes** (Role decomposition & reflection prompts) | No |
| **samarthshrivas/Agentra** | Voice interaction, wake-word, floating overlay assistant, tool registry | Native Android (Kotlin, Min SDK 26, Target SDK 35, Jetpack Compose) | **MIT** | **Yes** (Overlay lifecycle, voice pipeline, tool registry) | No |
| **8crsk/openclaw-android** (4AIs) | Multi-provider BYO-key, legend IDs, occlusion filtering, risk approvals | Hybrid: Kotlin UI + ShizukuBridge + Bundled Node.js (`libnode.so`) | **MIT** | **Partial** (Legend ID algorithm & approvals; eliminate Node.js) | No |
| **AnsonLai/Android-Use-Agent** | Broker state machine, human-in-the-loop steering, pause/stop recovery | Native Android (Kotlin, Jetpack Compose, Gemini Flash) | **GNU AGPL v3.0** | **NO** (Legal copyleft risk) | **YES** (Strict clean-room independent reimplementation) |
| **austinintelligence/android-use** | Semantic UI control, fallback hierarchies, device info bounding | Rust CLI + ADB daemon driving Android device | **MIT** | **No** (Rust/ADB runtime not suited for APK) | **Yes** (Semantic action schema reference) |
| **MadeAgents/mobile-use** | Hierarchical reflection, multi-agent planning, proactive exploration | Host Python framework + ADB + WebUI (NeurIPS'25, ColorAgent) | **MIT** | **Partial** (Port hierarchical reflection & state tracking logic) | No |
| **X-PLUG/MobileAgent** (v3.5 / GUI-Owl) | GUI foundation models (GUI-Owl 2B–235B), MCP/tool calling, VLA | Python, PyTorch, vLLM, Bailian API | **MIT** | **No** (Heavy model weights; remote inference only) | **Yes** (VLA prompt schemas, GUI-Owl integration spec) |
| **inclusionAI/UI-Venus** (UI-Venus-2) | Keypoint-grounded verification, verification-augmented reflection | Python, PyTorch, Chrome extension, ADB framework | **Apache 2.0** | **No** (Research training/eval harness) | **Yes** (Keypoint voting verification principles) |
| **xlang-ai/OpenCUA** | Computer-use foundation models, AgentNet dataset & trajectories | Python, PyTorch, vLLM, TypeScript (AgentNetTool) | **MIT** | **No** (Host training infrastructure) | **Yes** (Trajectory logging schemas & token formats) |
| **bytedance/UI-TARS** (v1.5 / v2.0) | Native GUI agent model, `MOBILE_USE` reasoning, grounding parser | Python, PyTorch, vLLM, transformers | **Apache 2.0** | **Partial** (Adopt `MOBILE_USE` action schema & coordinate parser) | No |
| **shivampkumar/taproot** | Grounding reliability, tree snapping, permission clearing, assertions | Python harness driving ADB on AndroidWorld / MobileWorld | **Apache 2.0** | **Yes** (Port tree-snap algorithm & permission auto-clearer to Kotlin) | No |
| **OSU-NLP-Group/UGround** | Universal GUI visual grounding model (UGround-V1 2B–72B) | Python, PyTorch, vLLM, HuggingFace weights (ICLR'25 Oral) | **MIT** | **No** (Heavy model weights; optional remote grounding provider) | **Yes** (Grounding benchmark reference) |
| **CVC2233/AndroTMem** | Anchored State Memory (ASM), causal dependency links, context drift | Python backend + Vue evaluation platform (ArXiv 2603.18429) | **MIT** | **Yes** (Port ASM data structures & causal link tracking to Room) | No |
| **UI-Mem** (ZJU / Alibaba) | Hierarchical experience memory, self-evolving experience reuse | Research framework for GUI online RL (ArXiv 2602.05832) | **Research / Academic** | **No** (RL training pipeline not on-device) | **Yes** (Hierarchical experience memory conceptual model) |
| **google-research/android_world** | Dynamic mobile environment benchmark & deterministic reward suite | Python + ADB test execution harness | **Apache 2.0** | **No** (Benchmark harness) | **Yes** (Evaluation task suite & verification criteria) |
| **mnotgod96/AppAgent** | Autonomous exploration phase & deployment execution | Python + ADB smartphone agent (CHI 2025) | **MIT** | **No** (Python host script) | **Yes** (Exploration and app-mapping logic reference) |

---

## 3. Deep-Dive Repository Inspections

### 3.1. adev0x/android-agent (Agent Phone)
* **Stack & Lifecycle:** Pure native Kotlin Android application. Implements a foreground `ScreenCaptureService` using Android `MediaProjection` and a native `AccessibilityActionService`.
* **Notable Reusable Mechanics:**
  1. *Coordinate Scaling Math:* Captures screenshots at 50% device resolution to minimize serialization latency, then applies linear scale factors ($S_x = W_{\text{real}}/W_{\text{cap}}$, $S_y = H_{\text{real}}/H_{\text{cap}}$) before dispatching tap coordinates.
  2. *Change Detection Engine:* Uses sampled pixel difference and a 64-bit difference hash (`dHash`) with Hamming distance thresholding ($D < 10$) to classify UI transitions into `NONE`, `MINOR` (loading/animation), or `SIGNIFICANT`.
  3. *Stuck Loop Breaker:* Tracks the last 4 perceptual screen hashes; if static, injects a `stuckHint` directly into the next LLM prompt turn.
  4. *Suspend Gestures:* Implements `tap()` and `swipe()` as Kotlin coroutine suspend functions awaiting `GestureResultCallback.onCompleted`.
* **Identified Deficiencies & Risks:**
  - Blind coordinate reliance: sends raw tap coordinates from the model without snapping to the accessibility hierarchy.
  - Basic single-provider Claude client without multi-model fallback or rotation.
  - Lacks structured memory and social/emotional awareness.

### 3.2. GiggleWang/MobileAgent-Android
* **Stack & Lifecycle:** Native Kotlin Android application implementing the multi-agent role decomposition of Mobile-Agent.
* **Notable Reusable Mechanics:**
  1. *Role Division:* Separates agent logic into `Manager` (high-level planner), `Executor` (subgoal execution), `Reflector` (post-action outcome evaluator), and `Notetaker` (task notes aggregator).
  2. *Screen Comparison Reflection:* Prompts evaluate pre-action vs post-action screenshots against the declared `ExpectedEffect`.
* **Identified Deficiencies & Risks:**
  - Multi-agent round-trips over mobile network multiply API latency by 3x–4x per step.
  - High token cost for continuous image uploads across all 4 agent roles.
  - Rudimentary error handling when network drops mid-reflection.

### 3.3. samarthshrivas/Agentra
* **Stack & Lifecycle:** Modern Kotlin codebase targeting Android 15 (API 35, Min SDK 26), AGP 8.7.0, Jetpack Compose.
* **Notable Reusable Mechanics:**
  1. *Floating Overlay Assistant:* Full `FloatingPromptService` lifecycle utilizing `SYSTEM_ALERT_WINDOW` with touch-through and expansion states.
  2. *Voice & Wake-Word Pipeline:* Dual integration of `WakeWordService` and `AssistantRecognitionService` allowing push-to-talk and voice activation.
  3. *Tool Abstraction:* Explicit `ToolDefinition` and `ToolRegistry` allowing dynamic registration of device capabilities (settings toggle, notifications, shell commands).
* **Identified Deficiencies & Risks:**
  - Action planner relies on direct LLM text output without rigorous grounding verification.
  - Incomplete memory management; conversation logs are kept in transient memory.

### 3.4. 8crsk/openclaw-android (4AIs)
* **Stack & Lifecycle:** Hybrid Android app containing a bundled Node.js runtime (`libnode.so` compiled for Termux environment) running a local gateway on `127.0.0.1:3000`, communicating with a Kotlin `AccessibilityService` via an internal NanoHTTPD bridge (`127.0.0.1:3001`).
* **Notable Reusable Mechanics:**
  1. *MarkAssigner & ServedLegend:* Translates accessibility nodes into numbered visual badges (legends) with bounding-box occlusion calculation. The model emits `tap 4` instead of arbitrary coordinates.
  2. *LegendDiff:* Emits precise delta of UI elements added/removed between consecutive actions.
  3. *ApprovalChannel:* Risk-graded intent classifier requiring explicit user authorization for sensitive actions.
  4. *EncryptedKeyStore:* Secure credential storage backed by Android KeyStore and EncryptedSharedPreferences.
* **Critical Architectural Decision (Node.js Elimination):**
  - **Verdict:** Do NOT adopt the bundled Node.js runtime. Running a full Node.js binary and executing `npm install` on an Android device adds ~60MB+ to APK size, introduces severe cold-start penalties (10–25s initial setup), creates fragile process-management failure points under Android LMK (Low Memory Killer), and violates ARYA's design requirement for a pure, robust native agent core. We will extract and rewrite the `MarkAssigner`, `OcclusionCalculator`, `LegendDiff`, and `ApprovalChannel` in pure native Kotlin.

### 3.5. AnsonLai/Android-Use-Agent (Virtuturtle)
* **Stack & Lifecycle:** Native Kotlin Android application with an `AutomationBroker` orchestrating interaction between the Compose UI and `UiAutomationService`.
* **Key Mechanisms Inspected:**
  1. *AutomationBroker:* State machine with clear transitions: `IDLE` $\to$ `COUNTDOWN` $\to$ `RUNNING` $\to$ `PAUSED` $\to$ `AWAITING_CONFIRMATION` $\to$ `COMPLETED` / `FAILED`.
  2. *Steering Channel:* Bidirectional steering protocol (`PENDING` $\to$ `DELIVERED` $\to$ `ACKNOWLEDGED`) enabling real-time human intervention during execution.
  3. *Critic Cadence:* Configurable verification frequency.
* **CRITICAL LEGAL & LICENSE AUDIT:**
  - **License:** **GNU Affero General Public License v3.0 (AGPL-3.0)**.
  - **Legal Assessment:** Under AGPL-3.0, any derivative work or project containing copied code must be licensed under AGPL-3.0 and provide full corresponding source code. This creates severe viral copyleft constraints that restrict licensing flexibility and enterprise deployment.
  - **Mandatory Action for ARYA:** **ZERO DIRECT CODE REUSE**. All broker state management, steering channels, and pause/resume primitives in ARYA must be designed and written completely from scratch (clean-room implementation) using standard Kotlin StateFlow / SharedFlow paradigms.

### 3.6. shivampkumar/taproot
* **Stack & Lifecycle:** Python automation harness targeting AndroidWorld and MobileWorld benchmarks over ADB.
* **Key Reusable Grounding Algorithms:**
  1. *Accessibility Tree Snapping (`snap`):*
     - If the LLM coordinate falls within clickable element bounding boxes, it snaps to the center of the *smallest* (most specific) element.
     - If outside, it snaps to the nearest clickable element center within a Euclidean threshold radius (`max_dist = 140px`).
     - Falls back to the raw coordinate only if no clickable candidate exists within range.
  2. *System Permission Interceptor (`_clear_permissions`):*
     - Pre-scans the UI hierarchy against known permission granting triggers (`"while using the app"`, `"allow only while using"`, `"only this time"`, `"allow"`) and auto-dismisses permission dialogs before task execution fails.
  3. *Deterministic Verification Assertions (`verify.py`):*
     - Normalizes unicode characters and validates post-action state using `text_contains`, `text_any`, and `activity_matches` predicates.
* **Adaptation Plan:** Port `snap()`, `_clear_permissions()`, and verification predicates directly into ARYA's native Kotlin `GroundingEngine`.

### 3.7. MadeAgents/mobile-use (ColorAgent / ColorGUI)
* **Stack & Lifecycle:** Python multi-agent framework driving Android devices over ADB; achieved 75% on AndroidWorld benchmark (NeurIPS 2025 Spotlight).
* **Key Innovations:**
  1. *Hierarchical Reflection:* Employs dual-level reflection: low-level action validation (did the tap register?) combined with high-level task progress evaluation (is the overall workflow advancing?).
  2. *Proactive Exploration:* When target elements are not immediately visible, executes controlled directional exploration (scrolling, view switching) rather than prematurely failing.
  3. *AppRetriever:* Dynamically selects relevant app knowledge and interaction guidelines based on package ID.
* **Adaptation Plan:** Integrate hierarchical reflection and proactive exploration into ARYA's `Reflector` and `Replanner` core.

### 3.8. CVC2233/AndroTMem (Anchored State Memory)
* **Stack & Lifecycle:** Research evaluation framework for long-horizon mobile GUI tasks (ArXiv 2603.18429, March 2026).
* **Core Insight:** In tasks spanning 20–60+ steps, failure is overwhelmingly driven by *memory breakdown and context drift* rather than perception errors. Replaying raw interaction trajectories floods context with noise, while simple summarization drops critical causal parameters (e.g. order numbers, confirmation codes, account IDs).
* **Anchored State Memory (ASM) Model:**
  - Formulates memory as a directed acyclic graph of **Intermediate State Anchors**.
  - Each anchor stores:
    1. `AnchorType`: Subgoal, intermediate value, branch decision, or dependency.
    2. `Content`: Structured semantic fact or parameter.
    3. `Evidence`: UI grounding proof (element text, resource ID, or visual signature).
    4. `CausalLinks`: Forward and backward edges representing step dependencies.
* **Adaptation Plan:** ASM will serve as the architectural foundation for ARYA's `WorkingMemory` and `EpisodicMemory` engines, persisted locally via Android Room.

### 3.9. inclusionAI/UI-Venus (UI-Venus-2)
* **Stack & Lifecycle:** Foundation GUI agent framework by Ant Group (August 2026, ArXiv 2609.00028).
* **Core Innovations:**
  1. *Keypoint-Grounded Verification:* Evaluates task completion by testing task-relevant visual keypoints rather than whole-screen subjective evaluation. Employs multi-model consensus voting across lightweight criteria to prevent reward hacking.
  2. *Verification-Augmented Reflection:* Couples verification feedback directly into the reflection step, helping the agent distinguish genuine completion from intermediate loading states.
* **Adaptation Plan:** Implement keypoint verification in ARYA's `VerifyEngine`.

### 3.10. bytedance/UI-TARS & X-PLUG/MobileAgent (GUI-Owl 1.5)
* **Stack & Lifecycle:** SOTA Vision-Language-Action foundation models for GUI interaction.
* **Core Takeaways:**
  1. Standardized mobile prompt schemas: `MOBILE_USE` (explicit actions: `tap`, `long_press`, `swipe`, `type`, `press_home`, `press_back`, `open_app`).
  2. Thought-Action formatting: Enforcing a strict internal reasoning step (`Thought: ...` followed by `Action: ...`) significantly enhances spatial reasoning before coordinate emission.
  3. Action serialization compatibility: ARYA's action engine will support standard UI-TARS action syntax for direct compatibility with open-weights GUI models.

---

## 4. Capability Categorization & Best-in-Class Selection

| Capability Area | Candidate Repositories | Selected Best-in-Class Reference | Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Android Execution Engine** | adev0x, GiggleWang, Agentra, openclaw, AnsonLai | **adev0x/android-agent** + **Agentra** | Pure native Kotlin, non-blocking coroutines, clean MediaProjection + AccessibilityService integration without external daemons. |
| **GUI Grounding & Reliability** | taproot, openclaw, UGround, UI-TARS | **shivampkumar/taproot** + **openclaw (MarkAssigner)** | Dual strategy: Hybrid Tree-Snapping (taproot) + Occlusion-filtered Legend ID badges (openclaw). Eliminates blind coordinate tapping. |
| **Action Verification** | taproot, UI-Venus-2, AnsonLai | **inclusionAI/UI-Venus-2** + **taproot** | Multi-attribute keypoint verification (text, activity, layout diff) with deterministic assertions. |
| **Reflection & Replanning** | mobile-use, GiggleWang, UI-Venus-2 | **MadeAgents/mobile-use** (Hierarchical) | Two-tier reflection: Action-level effect detection + Task-level milestone progress. Fast loop recovery. |
| **Long-Horizon Memory** | AndroTMem, UI-Mem, mobile-use | **CVC2233/AndroTMem** (Anchored State Memory) | Explicit state anchors with causal link tracking. Prevents context drift and state amnesia in 30+ step tasks. |
| **Voice & Overlay Interface** | Agentra, openclaw, adev0x | **samarthshrivas/Agentra** | Native Android 15 floating service, push-to-talk, wake-word hooks, and clean overlay permission handling. |
| **Model Routing & Key Vault** | openclaw, AnsonLai, GiggleWang | **8crsk/openclaw-android** | EncryptedSharedPreferences, dynamic provider catalogs (Gemini, Claude, OpenAI, Local), rate-limit fallback. |
| **Human Safety & Approvals** | openclaw, AnsonLai | **8crsk/openclaw-android** (ApprovalChannel concept) | Risk-graded action classification (Low/Medium/High/Critical) with mandatory biometric/PIN confirmation for irreversible actions. |

---

## 5. Architectural Elimination & Risk Mitigation Decisions

1. **Eliminate Embedded Node.js Runtime:**
   - *Issue:* `openclaw-android` bundles a 32MB Node.js ELF binary and unzips npm packages to execute a local gateway.
   - *Risk:* High memory footprint (~180MB RAM), frequent OOM kills by Android OS, 15-second cold starts, dependency on V8 native compilation.
   - *Mitigation:* ARYA implements its entire cognitive core, network dispatch, and tool orchestrator in **100% Native Kotlin** using Ktor/OkHttp and Kotlinx Serialization.

2. **Clean-Room Reimplementation of Broker & Steering (Avoid AGPL v3.0):**
   - *Issue:* `AnsonLai/Android-Use-Agent` contains high-value broker and steering logic, but is licensed under AGPL v3.0.
   - *Risk:* Legal copyleft contamination.
   - *Mitigation:* Zero code from `Android-Use-Agent` will be copied or imported. ARYA's `AgentBroker` will be developed independently using Kotlin `StateFlow`, `SharedFlow`, and structured concurrency.

3. **Eliminate Host ADB Requirement for Primary Execution:**
   - *Issue:* `mobile-use`, `taproot`, and `AppAgent` require a connected PC running ADB commands.
   - *Risk:* Inoperable as an autonomous standalone mobile agent.
   - *Mitigation:* ARYA executes all gestures, dumps, and key events directly via Android's native `AccessibilityService` and `MediaProjection`. ADB/Shizuku is retained strictly as an *optional* privileged acceleration layer.

4. **Multi-Model Fallback Against Rate Limits:**
   - *Issue:* Single-model dependencies fail when hitting API quotas or network timeouts.
   - *Mitigation:* Implement a hierarchical `ModelRouter` supporting Gemini 2.5/3.x, Anthropic Claude 3.7, OpenAI GPT-4o, and local ONNX/MediaPipe models with automatic key rotation and health tracking.

---

## 6. Audit Conclusion & Readiness

The repository audit confirms that while numerous partial implementations exist, no single repository delivers an end-to-end, native, safe, long-memory Android agent platform. ARYA synthesizes the validated best-in-class components into a cohesive, production-grade architecture without legal exposure or runtime baggage.

*Proceed to `ARYA_ARCHITECTURE.md` for full system specifications.*
