# ARYA — Autonomous Reactive Yield Agent

[![Platform](https://img.shields.io/badge/Platform-Android_14+-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

ARYA is a clean-room, production-grade Android AI digital operator engineered to perceive, ground, plan, act, reflect, and self-heal autonomously on native Android devices.

---

## The 9-Stage Cognitive Action Loop

```
OBSERVE -> UNDERSTAND -> PLAN -> ACT -> OBSERVE AGAIN -> COMPARE -> VERIFY -> REFLECT -> REPLAN / PROCEED
```

1. **Observe:** Gather Accessibility hierarchy, 64-bit perceptual hash (`dHash`), active package name, and frame bitmap.
2. **Understand:** Construct `ScreenState`, identifying active inputs, keyboard state, dialogs, and interactive targets.
3. **Plan:** Dynamic subgoal formulation, divergence detection, and 6-layer target grounding.
4. **Act:** 4-tier safety check (`RiskClassifier`), human approval interception (`RiskManager`), and gesture execution (`ActionEngine`).
5. **Observe Again:** Dynamic settle delay paced by SEAM policy; capture subsequent `ScreenState`.
6. **Compare:** Calculate structural difference and visual difference (Hamming distance).
7. **Verify:** Check deterministic assertions (expected text presence, activity transition, target element dismissal).
8. **Reflect:** Evaluate: $\text{PreviousState} + \text{Action} + \text{CurrentState} + \text{ExpectedEffect} \implies \text{OutcomeEvaluation}$.
9. **Replan / Proceed / Recover:** Advance working memory upon success; trigger self-healing jitter, modal dismissal, or backstep on failure; replan dynamically on screen divergence.

---

## 6-Layer GUI Grounding Hierarchy

1. **Priority 1:** Direct Accessibility Node Match (`viewIdResourceName` / ID)
2. **Priority 2:** Semantic UI Element (Fuzzy/exact text & content description match)
3. **Priority 3:** Legend ID Badge (Numbered element assignment via MarkAssigner)
4. **Priority 4:** On-Device ML Kit OCR Grounded Center Point
5. **Priority 5:** Taproot Grounding Coordinate Snapping (Smallest containing bounding area; Euclidean radius $R \le 140$px)
6. **Priority 6:** Viewport Clamped Coordinate Fallback (Bounded to physical screen dimensions)

---

## Tri-Tier Perception Escalation

- **Tier 1:** Accessibility Node Hierarchy (~15ms)
- **Tier 2:** On-Device Google ML Kit OCR (~60ms) for webviews, Flutter, and canvas apps
- **Tier 3:** Cloud Multimodal Vision Foundation Model (~1200ms)

---

## Core Subsystems

| Module | Component | Description |
| :--- | :--- | :--- |
| **01 Foundation** | `AgentBroker`, `AgentConsoleScreen` | Jetpack Compose HUD overlay, real-time telemetry, steering directives |
| **02 Accessibility** | `AryaAccessibilityService`, `AccessibilityReader` | High-speed tree traversal, bounds resolution, occlusion calculation |
| **03 Screen Perception** | `ScreenCaptureService`, `VisualHasher` | MediaProjection capture, 64-bit dHash perceptual fingerprinting |
| **04 Vision & OCR** | `OcrEngine`, `VisionEngine` | On-device ML Kit OCR provider, visual feature extractors |
| **05 GUI Grounding** | `GroundingEngine`, `GroundedTarget` | 6-layer priority grounding, taproot minimum-area spatial snapping |
| **06 Action Engine** | `ActionEngine`, `GestureController` | Gesture dispatching, observe-act-observe verification loops |
| **07 Dynamic Planner** | `TaskPlanner`, `TaskPlan` | Milestone subgoal formulation, dynamic divergence detection & replanning |
| **08 Reflection Engine**| `ReflectionEngine`, `ReflectionRecord` | Post-action verification, stuck loop detection ($\ge 3$ repeated hashes) |
| **09 Self-Recovery** | `RecoveryEngine`, `RecoveryStrategy` | $\pm 15$px spatial jitter, modal popup auto-dismissal, keyboard hiding, backstep |
| **10 Selective Memory**| `MemoryStore`, `MemoryGate` | Anchored State Memory (ASM), importance scoring filter, decay eviction |
| **11 Entity Context** | `EntityManager`, `PersonEntity` | Social contact resolution, nickname mapping, privacy redaction |
| **12 Emotion (SEAM)** | `EmotionalContextDetector` | Emotional cues detection, adaptive pacing delays (250ms–800ms) and verbosity |
| **13 Model Router** | `ModelRouter`, `KeyVault` | Dynamic routing (Gemini, Claude, OpenAI, Local), Keystore encryption, 429 backoff |
| **14 Voice System** | `VoicePipeline`, `VoiceRecognizer` | Push-to-talk, wake-phrase extraction ("Hey Arya"), interruptible TTS |
| **15 Safety & Risk** | `RiskManager`, `RiskClassifier` | 4-tier risk classification, payment/credential interception, emergency stop |
| **16 Privileged Bridges**| `PrivilegedExecutor`, `CapabilityManager`| Optional Shizuku/Root shell acceleration; non-root graceful degradation |
| **17 Benchmark Suite** | `BenchmarkSuite` | 21-point automated capability evaluation, 30-step long-horizon simulation |
| **18 Master Orchestrator**| `AgentEngine` | Master 9-stage loop uniting all subsystems end-to-end |

---

## Detailed Documentation
- [**Master Architecture Specification**](ARYA_ARCHITECTURE.md)
- [**Final Engineering Report**](ARYA_FINAL_REPORT.md)
- [**Repository Audit**](ARYA_REPOSITORY_AUDIT.md)
