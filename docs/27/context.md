## Context Summary: Issue #27 — [Setup] Scaffold Android project (Kotlin, Compose, Material 3, single-activity, nav shell)

### Issue
- **State:** open · **Labels:** `story`, `iteration-1` · **Assigned to:** javed-27 · **Story points:** 2

### What the issue asks for
Greenfield Android scaffold on the currently empty repo root: a Kotlin + Jetpack Compose + Material 3 project that builds, has a **single `MainActivity`** rendering a Compose screen, and a **navigation shell routing Splash Screen → Home Screen**. Build config must set `minSdk ≥ 26`, **latest stable `targetSdk`**, and `applicationId com.exammate`. Gradle version catalog + Kotlin DSL assumed; **navigation skeleton only** (no feature screens).

### Acceptance criteria
1. Builds successfully with Kotlin, Compose, Material 3
2. Single `MainActivity` renders a Compose screen
3. Nav shell routes Splash → Home
4. `minSdk ≥ 26`, latest stable `targetSdk`, `applicationId com.exammate`

### Linked issues
None referenced in the issue. Related iteration-1 issues (#28 Material 3 design system, #29 Home screen) are separate later stories — **out of scope** here.

### Images
| File | What it shows |
|------|--------------|
| `docs/images/home-screen.png`, `docs/ui/expected-ui.png` | Home/design mockups. **Note:** model cannot read images in this environment; design details taken from README text (M3, two feature cards, blue/purple palette). No images attached to the issue itself. |

### Codebase findings
| Area | Detail |
|------|--------|
| Repo structure | Root has only `README.md`, `plan.md`, `docs/`, `.opencode/`, `.git/` — **no Gradle/Kotlin sources anywhere** (greenfield) |
| Relevant files | `README.md` (full spec, M3 + nav flow), `plan.md` (master plan; 25 stories) |
| Prior planning artifacts | `docs/17/{context,plan,tasks}.md` — a **never-implemented** plan for a near-identical scaffold story (#17). Contains concrete decisions worth reusing: package `com.exammate`, display name "Exam Mate", version catalog layout, wrapper-committed strategy, 3-task breakdown |
| Test framework | None yet — to be scaffolded (JUnit4 unit + Compose UI test) |
| Tech stack | None yet — target: Kotlin 2.x, Compose BOM, Material 3, navigation-compose, version catalog + Kotlin DSL |

### Tooling on this machine
| Item | Status |
|------|--------|
| System JDK / Gradle | **Not installed** (`java`, `gradle` absent) |
| Homebrew | Available (6.0.15) — can install JDK/Gradle if needed |
| Android SDK | `~/Library/Android/sdk` with **only `platforms/android-37.0`**, `build-tools/36.0.0`, emulator + `system-images/android-37.1`, `platform-tools`, licenses accepted, **no cmdline-tools/sdkmanager** |
| Android Studio | Installed, bundling **JBR JDK 25** (`.../jbr/Contents/Home`) usable for builds |
| Emulator | AVD `Pixel_9_Pro` exists (API 37.1 system image) |

### Initial observations
- **`targetSdk` decision is driven by the local SDK:** the only installed platform is **API 37** (Android 17, stable in 2026), and "latest stable targetSdk" is an AC. So `compileSdk`/`targetSdk 37` is the natural pick → requires a **newer AGP (9.x) + Gradle 9.x** (classic AGP 8.13 caps at 36). Exact AGP/Gradle/Kotlin/Compose versions must be verified during planning.
- **Local verification is now feasible** (better than docs/17's "defer to Android Studio"): JDK 25 available via Android Studio JBR; emulator available for a Compose UI test. No cmdline-tools needed if compileSdk matches the installed platform.
- **Gradle wrapper bootstrap:** no system Gradle and no sdkmanager; wrapper must be bootstrapped by downloading a Gradle distribution directly (or installing Gradle via brew).
- **Reuse docs/17 conventions:** `com.exammate` package/applicationId, "Exam Mate" label, version catalog at `gradle/libs.versions.toml`, `.gitignore`, `proguard-rules.pro`, JUnit4 + Compose UI test scaffolds.
- Splash → Home nav: minimal `navigation-compose` with two destinations; Home is a placeholder screen (feature screens belong to #29).
