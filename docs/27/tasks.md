# Tasks: [Setup] Scaffold Android project (Kotlin, Compose, Material 3, single-activity, nav shell) (#27)

## Task 1: Gradle project skeleton (Kotlin DSL + version catalog + wrapper)

**Description**
Create the root-level Gradle build for a Kotlin + Compose Android project using Kotlin DSL, a version catalog, and a committed Gradle wrapper. Repo root is the project root; single `:app` module included. Corresponds to plan's Approach "Toolchain/Wrapper bootstrap" and root build Affected Areas.

**Acceptance Criteria**
- [ ] `settings.gradle.kts` with `pluginManagement` (google(), mavenCentral(), gradlePluginPortal()) + `dependencyResolutionManagement` (google(), mavenCentral()), `rootProject.name`, `include(":app")`
- [ ] Root `build.gradle.kts` declares AGP + Compose compiler plugin (`apply false`) only
- [ ] `gradle/libs.versions.toml` version catalog with AGP 9.2.0, Compose BOM 2026.06.00, navigation-compose 2.9.8, activity-compose 1.13.0, core-ktx 1.19.0, lifecycle-runtime-ktx 2.11.0, junit, androidx-test-ext-junit, espresso — all stable/resolvable
- [ ] `gradle.properties` with `org.gradle.jvmargs` and `android.useAndroidX=true`
- [ ] `.gitignore` covers `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.kotlin/`
- [ ] Gradle wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties` pinned to Gradle 9.4.1)
- [ ] No secrets/hardcoded paths; TOML/Gradle files syntactically well-formed

**Files Likely Affected**
- `settings.gradle.kts` — root build setup
- `build.gradle.kts` — root plugin declarations
- `gradle/libs.versions.toml` — dependency/plugin versions
- `gradle.properties` — project-wide properties
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — wrapper (bootstrap via downloaded Gradle 9.4.1 dist)
- `.gitignore` — build artifacts

**Test Requirements**
- Unit: none (build config)
- Verification: `./gradlew help` and `./gradlew projects` succeed with `JAVA_HOME` pointing at Android Studio JBR 25 (fallback JDK 21); structural validation of all files

**Dependencies**
- None

**Estimated Complexity**
M

---

## Task 2: `:app` module — build config, manifest, resources, launcher icon

**Description**
Add the `:app` module: Compose build config (minSdk 26, compileSdk/targetSdk 37), manifest shell (application element — no activity yet), string/theme resources, and a minimal adaptive launcher icon with display name "Exam Mate". Matches plan's app-module Affected Areas.

**Acceptance Criteria**
- [ ] `app/build.gradle.kts` applies `com.android.application` + Compose compiler plugin; `namespace`/`applicationId` = `com.exammate`; minSdk 26, compileSdk/targetSdk 37; `buildFeatures.compose = true`; Java/`jvmTarget` 17; release buildType wired with `proguard-rules.pro`
- [ ] `app/proguard-rules.pro` placeholder present
- [ ] `AndroidManifest.xml` declares `<application>` with label "Exam Mate", theme, and adaptive launcher icon (activity declared in Task 3)
- [ ] `values/strings.xml`, `values/themes.xml`, `values-night/themes.xml` (base window theme parent `android:Theme.Material.*.NoActionBar` — Compose theme is applied in Task 3)
- [ ] Minimal adaptive launcher icon: `mipmap-anydpi-v26` adaptive icon (simple foreground vector + background color) + legacy `mipmap-*` fallbacks for API < 26
- [ ] No `local.properties`, no hardcoded absolute paths, no build/auth artifacts committed
- [ ] `./gradlew :app:assembleDebug` builds successfully (no Kotlin sources required)

**Files Likely Affected**
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/{strings,themes}.xml`, `values-night/themes.xml`
- `app/src/main/res/drawable/*`, `mipmap-anydpi-v26/*`, `mipmap-*/*`

**Test Requirements**
- Unit: none
- Integration: `./gradlew :app:assembleDebug` produces a valid APK; resource/manifest merge clean

**Dependencies**
- Depends on Task 1

**Estimated Complexity**
M

---

## Task 3: App source — single `MainActivity`, M3 theme, nav shell (Splash → Home)

**Description**
Add the app's Kotlin source: single `MainActivity` hosting Compose, a minimal Material 3 theme (light/dark, blue/purple), and the `navigation-compose` shell routing Splash (auto-advance ~1.5s) → placeholder Home. Declares the activity in the manifest. Matches plan's "Nav shell" approach and source Affected Areas.

**Acceptance Criteria**
- [ ] `MainActivity.kt` is a single activity calling `setContent { ExamMateTheme { AppNavHost() } }`
- [ ] `ui/theme/{Color,Theme,Type}.kt` define an M3 light + dark color scheme (blue primary / purple secondary per README), `ExamMateTheme` composable, typography
- [ ] `navigation/AppNavHost.kt` builds a `NavHost` with routes `splash` and `home`
- [ ] `ui/splash/SplashScreen.kt` shows app content, then auto-navigates to Home after ~1.5s via `LaunchedEffect`, clearing the back stack (`popUpTo(...) { inclusive = true }`)
- [ ] `ui/home/HomeScreen.kt` is a minimal placeholder (feature cards belong to #29)
- [ ] Manifest declares `MainActivity` as the single exported launcher activity (`MAIN`/`LAUNCHER` intent filter)
- [ ] `./gradlew :app:compileDebugKotlin` and `:app:assembleDebug` succeed

**Files Likely Affected**
- `app/src/main/java/com/exammate/MainActivity.kt`
- `app/src/main/java/com/exammate/ui/theme/{Color,Theme,Type}.kt`
- `app/src/main/java/com/exammate/navigation/AppNavHost.kt`
- `app/src/main/java/com/exammate/ui/splash/SplashScreen.kt`
- `app/src/main/java/com/exammate/ui/home/HomeScreen.kt`
- `app/src/main/AndroidManifest.xml` (activity declaration)

**Test Requirements**
- Unit: none directly
- Integration: Compose UI navigation test (Task 4) asserts Splash renders then Home after nav; manual launch check via emulator/Android Studio

**Dependencies**
- Depends on Task 2

**Estimated Complexity**
M

---

## Task 4: Test scaffolds + full verification (unit + Compose UI navigation test)

**Description**
Add test scaffolds and run the full verification suite: JUnit4 unit test, an instrumented Compose UI test asserting Splash → Home navigation, then `assembleDebug`, `test`, and `connectedDebugAndroidTest` on the Pixel_9_Pro emulator. Matches plan's Verification section and tests Affected Areas.

**Acceptance Criteria**
- [ ] `app/src/test/.../ExampleUnitTest.kt` (JUnit4) present and passing under `./gradlew test`
- [ ] `app/src/androidTest/.../AppNavigationTest.kt` (Compose UI via `createAndroidComposeRule<MainActivity>`) asserts Splash content is displayed, advances the test clock past the splash delay, then asserts Home content is displayed
- [ ] Test dependencies wired in `app/build.gradle.kts`: `testImplementation` junit; `androidTestImplementation` androidx-test-ext-junit + espresso + compose `ui-test-junit4`; `debugImplementation` compose `ui-test-manifest` + `ui-tooling`
- [ ] `./gradlew assembleDebug` succeeds (AC: project builds)
- [ ] `./gradlew test` succeeds (unit test green)
- [ ] `./gradlew connectedDebugAndroidTest` succeeds on the Pixel_9_Pro emulator (best-effort; if the emulator cannot run, document the manual launch check performed instead)

**Files Likely Affected**
- `app/src/test/java/com/exammate/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/exammate/AppNavigationTest.kt`
- `app/build.gradle.kts` (test dependencies)

**Test Requirements**
- Unit: `ExampleUnitTest` under `./gradlew test`
- Integration: `AppNavigationTest` under `./gradlew connectedDebugAndroidTest` on emulator

**Dependencies**
- Depends on Task 3

**Estimated Complexity**
M
