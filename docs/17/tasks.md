# Tasks: [Setup] Initialize Android project (Kotlin, Compose, min SDK) (#17)

## Task 1: Gradle project skeleton (Kotlin DSL + version catalog + wrapper)

**Description**
Create the root-level Gradle build for a Kotlin + Compose Android project, using Kotlin DSL, a version catalog, and a committed Gradle wrapper. Repo root is the project root; single `:app` module included.

**Acceptance Criteria**
- [ ] `settings.gradle.kts` with pluginManagement + dependencyResolutionManagement (google(), mavenCentral()), includes `:app`
- [ ] Root `build.gradle.kts` declares AGP + Kotlin Android plugin (`apply false`) only
- [ ] `gradle/libs.versions.toml` version catalog with AGP, Kotlin, Compose BOM, core-ktx, activity-compose, lifecycle, junit, androidx-test — all stable, resolvable versions
- [ ] `gradle.properties` with `android.useAndroidX=true` and JVM args
- [ ] Gradle wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties` pinned to the AGP-compatible Gradle version)
- [ ] `.gitignore` covers `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`
- [ ] Structural check: all files present, TOML/Gradle files syntactically well-formed

**Files Likely Affected**
- `settings.gradle.kts` — root build setup
- `build.gradle.kts` — root plugin declarations
- `gradle/libs.versions.toml` — dependency/plugin versions
- `gradle.properties` — project-wide Gradle properties
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — wrapper
- `.gitignore` — ignore build artifacts

**Test Requirements**
- Unit: none (build config)
- Verification: `./gradlew help` runs once a JDK + Android SDK are available (deferred to user's Android Studio environment per plan Assumption 3); structural validation here

**Dependencies**
- None

**Estimated Complexity**
M

---

## Task 2: `:app` module — single-activity Compose app with toffee launcher

**Description**
Add the `:app` module: Compose build config (minSdk 26, compileSdk/targetSdk 36), manifest, single `MainActivity`, Material 3 theme, string resources, and an adaptive toffee-candy launcher icon with display name "Exam Mate".

**Acceptance Criteria**
- [ ] `app/build.gradle.kts` applies `com.android.application` + Kotlin Android + Compose compiler plugin; `namespace`/`applicationId` = `com.exammate`; minSdk 26, compileSdk/targetSdk 36; `buildFeatures.compose = true`; jvmTarget 17
- [ ] `AndroidManifest.xml` declares exactly one activity (`MainActivity`), exported, with `MAIN`/`LAUNCHER` intent filter, label "Exam Mate", and the adaptive launcher icon
- [ ] `MainActivity.kt` is a single Composable-hosting activity rendering a simple "Exam Mate" welcome screen
- [ ] Material 3 theme (light + dark) with Color/Theme/Type files and `themes.xml`
- [ ] Adaptive launcher icon: `mipmap-anydpi-v26` foreground vector (brown/amber toffee candy) on cream background; legacy fallback drawables for API < 26
- [ ] `proguard-rules.pro` placeholder present and wired into release buildType
- [ ] No `local.properties`, no hardcoded absolute paths, no build/auth artifacts committed

**Files Likely Affected**
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/exammate/MainActivity.kt`
- `app/src/main/java/com/exammate/ui/theme/{Color,Theme,Type}.kt`
- `app/src/main/res/values/{strings,colors,themes}.xml`, `values-night/themes.xml`
- `app/src/main/res/drawable/*`, `app/src/main/res/mipmap-anydpi-v26/*`
- `app/proguard-rules.pro`

**Test Requirements**
- Integration: Compose UI test (Task 3) asserts the welcome screen renders; build-level verification deferred to Android Studio

**Dependencies**
- Depends on Task 1

**Estimated Complexity**
M

---

## Task 3: Test scaffolds — unit test + Compose UI test

**Description**
Add the standard test scaffolds: a JUnit4 unit test and an instrumented Compose UI test asserting the welcome screen content renders.

**Acceptance Criteria**
- [ ] `app/src/test/.../ExampleUnitTest.kt` (JUnit4) present and correct
- [ ] `app/src/androidTest/.../ExampleInstrumentedTest.kt` (Compose UI test via `createAndroidComposeRule`) asserts the "Exam Mate" welcome text is displayed
- [ ] Test dependencies wired in `app/build.gradle.kts`: `testImplementation` junit; `androidTestImplementation` androidx.test.ext:junit + espresso + compose-ui-test-junit4; `debugImplementation` ui-test-manifest + ui-tooling
- [ ] Tests follow package `com.exammate`

**Files Likely Affected**
- `app/src/test/java/com/exammate/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/exammate/ExampleInstrumentedTest.kt`
- `app/build.gradle.kts` (test dependencies)

**Test Requirements**
- Unit: `ExampleUnitTest` runs under `./gradlew test`
- Integration: Compose UI test runs under `./gradlew connectedAndroidTest` on an emulator (deferred to user's environment)

**Dependencies**
- Depends on Task 2

**Estimated Complexity**
S
