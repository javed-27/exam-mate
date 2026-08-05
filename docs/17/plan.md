# Plan: [Setup] Initialize Android project (Kotlin, Compose, min SDK) (#17)

## Objective
A clean, buildable Kotlin + Jetpack Compose single-activity Android project scaffolded at the repo root, ready to open in Android Studio. Serves as the foundation for the AI Screen Reader Assistant.

## Scope
### In Scope
- Gradle Kotlin DSL build with version catalog (`gradle/libs.versions.toml`) and Gradle wrapper
- Single `:app` module, single `MainActivity` (Compose), Material 3 theme
- minSdk 26, compileSdk/targetSdk 36 (Android 16, latest stable supported by classic AGP line)
- Launcher: toffee candy vector adaptive icon (brown/amber on cream), display name **"Exam Mate"**
- Test scaffolds: JUnit4 unit test + Compose UI test
- `.gitignore`, ProGuard rules, package `com.exammate`

### Out of Scope
- MediaProjection/screen-reader logic, DI, navigation, backend, auth, CI

## Approach
- **Toolchain (recommendation):** AGP 8.13.x + Gradle 8.14.x + Kotlin 2.2.x + Compose compiler plugin (Kotlin 2.x built-in). Chosen over AGP 9.x (built-in Kotlin) for stability/wide compatibility. **Trade-off:** caps targetSdk at 36; if targetSdk 37 is required, switch to AGP 9.2.x + Gradle 9.4.1.
- **Versions:** pinned at implementation time from plugin portal / Maven (Compose BOM, activity-compose, core-ktx, lifecycle) — the scaffold's `libs.versions.toml` will hold known-good stable versions.
- **Wrapper:** `gradle-wrapper.jar`, `gradlew`, `gradlew.bat` fetched from the official Gradle repo/tag so `./gradlew` works out of the box.
- **Verification:** no JDK/SDK on this machine, so verification = structural checks here + user runs Gradle sync/build in Android Studio (matches the issue's IDE-based ACs). All dependencies via Google/Maven Central only (repo buildable without auth).

## Affected Areas
| Area | Files | Change Type |
|------|-------|-------------|
| Build | `settings.gradle.kts`, `build.gradle.kts`, `gradle/` (libs.versions.toml, wrapper), `gradle.properties` | Add |
| App | `app/` — build.gradle.kts, AndroidManifest, MainActivity, ui/theme, res (strings/colors/themes, adaptive launcher) | Add |
| Tests | `app/src/test/`, `app/src/androidTest/` | Add |
| Root | `.gitignore`, `proguard-rules.pro` | Add |

## Assumptions
1. [ASSUMPTION] Package/applicationId: `com.exammate`; repo root is the project root (monorepo layout not needed).
2. [ASSUMPTION] Classic AGP 8.13 stack with targetSdk 36 (see trade-off above).
3. [ASSUMPTION] Verification deferred to Android Studio; wrapper committed so CLI works once tooling is installed.
4. [ASSUMPTION] Display name "Exam Mate", toffee adaptive icon as discussed.

## Open Questions (resolved)
| # | Question | Answer / Decision |
|---|----------|------------------|
| 1 | Package / applicationId | `com.exammate` |
| 2 | AGP generation | AGP 8.13.x classic (targetSdk 36) |
| 3 | Local verification | Deferred to Android Studio (no local tooling) |
| 4 | Launcher icon / name | Toffee candy adaptive icon, "Exam Mate" |

## Risks & Mitigations
| Risk | Mitigation |
|------|-----------|
| Version catalog pins are wrong at build time | Verify against Google Maven / plugin portal during implementation; use stable versions |
| Wrapper jar fetch fails | Fall back to downloading the Gradle distribution and running `gradle wrapper` |
| AGP 9 built-in Kotlin migration later | Boring classic setup now; upgrade path documented by JetBrains when needed |
