# AI Screen Reader Assistant — Complete Plan

## Overview

An Android app that reads a question displayed on the screen and returns the answer using a local (default) or cloud LLM. The user triggers capture manually — there is **no continuous screen monitoring**.

## Product flow (click-capture model)

1. User opens the app and taps **Start Reader** on Home.
2. The system **MediaProjection permission** dialog appears.
3. On grant, a **foreground service** starts and the in-app **Monitor view** opens.
4. User switches to the exam app and sees a question.
5. User taps **Capture** — on the floating overlay (over other apps) or in the Monitor view.
6. A **single screenshot** is taken → **OCR** → **question parsing** → **LLM prompt** → **answer**.
7. The answer is displayed in the overlay/Monitor view and saved to **History**.
8. **Settings** configures provider, model, server, and API key.

```
tap Capture → screenshot → OCR → parse question → build prompt → LLM → answer → overlay + history
```

## Goals

- Answer on-screen multiple-choice questions without leaving the app.
- Default LLM is **local and free** (Ollama); cloud (OpenAI/Gemini) is optional.
- Fully on-device OCR (ML Kit), no network needed for text reading.
- Minimal, non-intrusive floating overlay.
- Answer history stored on-device.

## Non-goals

- No continuous screen monitoring or auto-detection of changes.
- No background answering when the user has not tapped Capture.
- No cloud account or backend required.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3), single-activity
- **Gradle version catalog** (`libs.versions.toml`), modular packages/modules
- **MediaProjection** + **VirtualDisplay** + **ImageReader** for on-demand screenshots
- **Foreground service** (`mediaProjection` type) to keep the capture session alive
- **Google ML Kit** Text Recognition (on-device OCR)
- **Retrofit + OkHttp** for LLM HTTP calls
- **Room** for answer history
- **DataStore** for settings; **Keystore/EncryptedSharedPreferences** for API keys
- **Coroutines/Flow** throughout
- minSdk 26+, targetSdk latest stable

## Architecture

Layers: **UI (Compose)** → **ViewModel/StateFlow** → **UseCases/Repository** → **data sources** (capture service, ML Kit, Retrofit, Room).

Concerns separated into modules/packages: `core`, `capture`, `ocr`, `question`, `llm`, `overlay`, `settings`, `history`.

## Iterations and stories

Total: **25 stories, 76 story points**.

### Iteration 1 — Setup (7 pts)
| # | Story | Pts |
|---|---|---|
| #17 | [Setup] Initialize Android project (Kotlin, Compose, min SDK) | 2 |
| #18 | [Setup] Modular build config (version catalog, module split) | 2 |
| #19 | [Setup] App shell & Compose navigation scaffold | 3 |

### Iteration 2 — Screen Capture (10 pts)
| # | Story | Pts |
|---|---|---|
| #20 | [Capture] MediaProjection permission flow (grant/deny) | 2 |
| #21 | [Capture] Foreground capture service (start/stop + notification) | 3 |
| #22 | [Capture] Single screenshot via ImageReader/VirtualDisplay | 5 |

### Iteration 3 — OCR (5 pts)
| # | Story | Pts |
|---|---|---|
| #23 | [OCR] ML Kit text recognizer setup | 2 |
| #24 | [OCR] Bitmap to recognized-text pipeline | 3 |

### Iteration 4 — Trigger & Orchestration (6 pts)
| # | Story | Pts |
|---|---|---|
| #25 | [Capture] In-app monitor view with Capture button & status | 3 |
| #1 | [Capture] Single-shot capture trigger & pipeline orchestration | 3 |

### Iteration 5 — Question Extraction (5 pts)
| # | Story | Pts |
|---|---|---|
| #2 | [Question] Parse OCR output into question + options | 3 |
| #3 | [Question] Build LLM prompt from parsed question | 2 |

### Iteration 6 — LLM (11 pts)
| # | Story | Pts |
|---|---|---|
| #4 | [LLM] Client interface (Retrofit/OkHttp contract) | 2 |
| #5 | [LLM] Ollama provider (default, local/free) | 3 |
| #6 | [LLM] End-to-end answer flow with error/retry | 3 |
| #16 | [Cloud] Cloud provider behind interface (OpenAI/Gemini, optional) | 3 |

### Iteration 7 — Overlay (8 pts)
| # | Story | Pts |
|---|---|---|
| #7 | [Overlay] Overlay permission (SYSTEM_ALERT_WINDOW) | 1 |
| #8 | [Overlay] Draggable floating overlay with Capture button + dismiss | 5 |
| #9 | [Overlay] Wire LLM answer stream into overlay | 2 |

### Iteration 8 — Performance (11 pts)
| # | Story | Pts |
|---|---|---|
| #10 | [Perf] Single-flight capture & request guard | 3 |
| #11 | [Perf] Memory tuning (downscale frames, buffer reuse) | 3 |
| #12 | [Perf] Response caching to skip duplicate questions | 5 |

### Iteration 9 — Settings & History (13 pts)
| # | Story | Pts |
|---|---|---|
| #13 | [Settings] Settings screen (provider, model, toggle, API key) | 5 |
| #14 | [History] Room persistence for Q&A | 5 |
| #15 | [History] History list + detail screen | 3 |

## Delivery order

Implementation is strictly sequential by iteration; within an iteration stories can be picked in any order (all start "ready").

**#17 → #18 → #19 → #20 → #21 → #22 → #23 → #24 → #25 → #1 → #2 → #3 → #4 → #5 → #6 → #7 → #8 → #9 → #10 → #11 → #12 → #13 → #14 → #15** (then #16 optional).

## Expected UI

Reference design for the Monitor view / capture screen:

![Expected UI](docs/ui/expected-ui.png)

## Working agreement

- Each story: "As a … I want … so that …" + Acceptance Criteria (Given/When/Then) + Assumptions (with story points).
- Stories live as GitHub issues on `javed-27/exam-mate`, labeled `story` + `iteration-N`.
- Work one iteration at a time; do not start a story until its prerequisites are merged.
