# Plan: [Setup] Scaffold Android project (Kotlin, Compose, Material 3, single-activity, nav shell) (#27)

## Objective
A clean, buildable, single-activity Kotlin + Jetpack Compose + Material 3 Android app at the repo root with a `navigation-compose` shell (Splash → Home), `applicationId com.exammate`, `minSdk 26`, `compileSdk`/`targetSdk 37` — fully verified locally (build + tests) so later stories build on a solid foundation.

## Scope
### In Scope
- Gradle Kotlin DSL skeleton (version catalog + committed wrapper)
- Single `:app` module
- `MainActivity` + minimal Material 3 theme (light/dark, blue/purple)
- Nav shell with Splash (auto-advance ~1.5s) and placeholder Home
- Minimal adaptive launcher icon, display name "Exam Mate"
- JUnit4 unit test + Compose UI navigation test
- Local verification: build + unit + emulator UI test

### Out of Scope
- Home feature cards (#29)
- Full Material 3 design system (#28)
- Splash-screen API polish (androidx splashscreen)
- CI, DI, custom artwork
- Reviving any `docs/17` code (never implemented; #27 supersedes it)

## Approach
- **Toolchain:** AGP 9.2.0 + Gradle 9.4.1 + Kotlin **built-in** via AGP 9 (no `kotlin-android` plugin; AGP bundles KGP 2.2.10). Compose compiler plugin pinned to match the bundled KGP, verified at implementation time.
- **Libraries:** Compose BOM `2026.06.00`, `navigation-compose 2.9.8`, `activity-compose 1.13.0`, `core-ktx 1.19.0`, `lifecycle-runtime-ktx 2.11.0`, JUnit4, `androidx.test.ext:junit`, Compose `ui-test-junit4` + `ui-test-manifest`.
- **Config:** `namespace`/`applicationId` = `com.exammate`, `minSdk 26`, `compileSdk`/`targetSdk 37`, JVM target 17, version catalog at `gradle/libs.versions.toml`, AndroidX enabled.
- **Nav shell:** `AppNavHost()` in `MainActivity` under `ExamMateTheme`; routes `splash` and `home`. Splash composable uses `LaunchedEffect` delay then navigates to Home with `popUpTo(inclusive)`.
- **Wrapper bootstrap:** no system Gradle → download `gradle-9.4.1-bin.zip`, generate wrapper, commit `gradlew`/`gradlew.bat`/`gradle/wrapper/*`.
- **JDK:** Android Studio JBR 25 via `JAVA_HOME` (env var, not committed); fallback `brew install --cask temurin@21`. SDK located via gitignored `local.properties`.

## Verification
1. `./gradlew assembleDebug` — project builds
2. `./gradlew test` — unit test passes
3. `./gradlew connectedDebugAndroidTest` on the existing **Pixel_9_Pro** emulator — UI test asserts Splash renders, then Home renders after nav (best-effort; falls back to manual launch check in Android Studio if the emulator misbehaves)

## Affected Areas
| Area | Files | Change Type |
|------|-------|-------------|
| Root build | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `proguard-rules.pro` | Add |
| Version catalog | `gradle/libs.versions.toml` | Add |
| Wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Add |
| App module | `app/build.gradle.kts`, `app/proguard-rules.pro` | Add |
| Manifest/res | `app/src/main/AndroidManifest.xml`, `values/{strings,themes}`, `values-night/themes`, `mipmap-anydpi-v26/*`, launcher foreground/background drawables | Add |
| Source | `MainActivity.kt`, `ui/theme/{Color,Theme,Type}.kt`, `ui/splash/SplashScreen.kt`, `ui/home/HomeScreen.kt`, `navigation/AppNavHost.kt` | Add |
| Tests | `app/src/test/...`, `app/src/androidTest/...` | Add |

## Assumptions
1. [ASSUMPTION] Display name "Exam Mate"; minimal adaptive launcher icon (no custom artwork).
2. [ASSUMPTION] Splash auto-advances after ~1.5s via `LaunchedEffect`.
3. [ASSUMPTION] JBR 25 (Android Studio) drives the Gradle build; JDK 21 via brew is the fallback.
4. [ASSUMPTION] Minimal static light/dark M3 theme; dynamic color / full design system deferred to #28.
5. [ASSUMPTION] Compose compiler plugin version == bundled KGP version, pinned at implementation after verification.

## Open Questions (resolved)
| # | Question | Answer / Decision |
|---|----------|------------------|
| 1 | Display name | "Exam Mate" |
| 2 | Launcher icon | Minimal adaptive icon (simple vector, no artwork) |
| 3 | Verification depth | Full local: build + unit + emulator UI test (best-effort emulator) |
| 4 | Splash behavior | Auto-advance ~1.5s |

## Risks & Mitigations
| Risk | Mitigation |
|------|-----------|
| Compose compiler plugin mismatches bundled KGP | Verify at impl; bump KGP via classpath if needed |
| JBR 25 rejected by Gradle 9.4.1 | Fall back to `temurin@21` |
| Emulator UI test slow/flaky | Best-effort; manual Android Studio launch check as fallback |
| Large first-build downloads | One-time cost; versions all stable |
| Wrapper dist download fails | Retry mirror / fall back to `brew install gradle` for bootstrap |

## Status: Completed
Implemented in 4 tasks. All tests passing. Final commit: adf836b.
- Task 1: Gradle project skeleton (Kotlin DSL + version catalog + wrapper) — `a4e3a73`
- Task 2: `:app` module build config, manifest, resources, launcher icon — `762553a`
- Task 3: MainActivity, M3 theme, nav shell (Splash → Home) — `831ce5e`
- Task 4: Unit + Compose UI navigation tests, full verification — `adf836b`

Verification: `assembleDebug`, `testDebugUnitTest`, and `connectedDebugAndroidTest`
(2/2 passing on Pixel_9_Pro, API 37). Toolchain: AGP 9.2.0, Gradle 9.4.1, Kotlin 2.3.21
(built-in via AGP 9), Compose BOM 2026.06.00, compileSdk/targetSdk 37, minSdk 26.
