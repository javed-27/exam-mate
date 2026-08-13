package com.exammate.ui.mcq

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.exammate.mcq.CameraPermissionStore
import com.exammate.mcq.DefaultScreenCaptureRequester
import com.exammate.mcq.McqAnswerState
import com.exammate.mcq.McqAnswerStateSaver
import com.exammate.mcq.McqPermissionState
import com.exammate.mcq.McqPipeline
import com.exammate.mcq.McqSolverPipeline
import com.exammate.mcq.McqServerSettings
import com.exammate.mcq.PermissionAction
import com.exammate.mcq.ScreenCaptureRequester
import com.exammate.mcq.SharedPrefsCameraPermissionStore
import com.exammate.mcq.SharedPrefsMcqServerSettings
import com.exammate.mcq.StepStatus
import com.exammate.mcq.ai.OllamaMcqClient
import com.exammate.mcq.nextAction
import com.exammate.mcq.ocr.MlKitOcrService
import kotlinx.coroutines.launch

internal const val CAMERA_PREVIEW_TAG = "camera_preview"
internal const val SERVER_URL_FIELD_TAG = "server_url_field"
internal const val MODEL_FIELD_TAG = "model_field"

@Composable
fun McqSolverScreen(
    onBack: () -> Unit,
    onBackToHome: () -> Unit = onBack,
    checker: McqPermissionChecker? = null,
    cameraPermissionStore: CameraPermissionStore? = null,
    screenCaptureRequester: ScreenCaptureRequester? = null,
    serverSettings: McqServerSettings? = null,
    initialScreenCaptureGranted: Boolean = false,
    requestCamera: ((permission: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    pipeline: McqPipeline? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val effectiveChecker = checker ?: remember { AndroidPermissionChecker(context) }
    val effectiveStore = cameraPermissionStore ?: remember { SharedPrefsCameraPermissionStore(context) }
    val effectiveRequester = screenCaptureRequester ?: remember { DefaultScreenCaptureRequester() }
    val effectiveSettings = serverSettings ?: remember { SharedPrefsMcqServerSettings(context) }
    val effectivePipeline = pipeline ?: remember {
        McqSolverPipeline(
            ocr = MlKitOcrService(),
            aiClient = OllamaMcqClient(
                baseUrl = { effectiveSettings.baseUrl },
                model = { effectiveSettings.model },
            ),
        )
    }
    val captureScope = rememberCoroutineScope()

    var cameraGranted by remember { mutableStateOf(effectiveChecker.isCameraGranted()) }
    var cameraDeniedFlag by remember { mutableStateOf(effectiveStore.cameraDenied) }
    var accessibilityGranted by remember { mutableStateOf(effectiveChecker.isAccessibilityEnabled()) }
    var accessibilityDenied by rememberSaveable { mutableStateOf(false) }
    var screenCaptureGranted by rememberSaveable { mutableStateOf(initialScreenCaptureGranted) }
    var screenCaptureDenied by rememberSaveable { mutableStateOf(false) }
    var settingsOpened by remember { mutableStateOf(false) }
    var answerState by rememberSaveable(stateSaver = McqAnswerStateSaver) {
        mutableStateOf(McqAnswerState.WaitingForOcr)
    }
    var showServerSettings by remember { mutableStateOf(false) }

    val onCameraResult: (Boolean) -> Unit = { granted ->
        if (granted) {
            cameraGranted = true
            cameraDeniedFlag = false
            effectiveStore.cameraDenied = false
        } else {
            cameraDeniedFlag = true
            effectiveStore.cameraDenied = true
            onBackToHome()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onCameraResult(granted) }
    val launchCameraRequest: (String) -> Unit = { permission ->
        if (requestCamera == null) {
            cameraLauncher.launch(permission)
        } else {
            requestCamera(permission, onCameraResult)
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val parsed = effectiveRequester.parseResult(result.resultCode, result.data)
        if (parsed.granted) {
            screenCaptureGranted = true
            screenCaptureDenied = false
        } else {
            screenCaptureDenied = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && settingsOpened) {
                settingsOpened = false
                if (effectiveChecker.isAccessibilityEnabled()) {
                    accessibilityGranted = true
                    accessibilityDenied = false
                } else {
                    accessibilityDenied = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(effectivePipeline) {
        onDispose {
            if (pipeline == null) effectivePipeline.close()
        }
    }

    val state = McqPermissionState(
        camera = if (cameraGranted) StepStatus.GRANTED else StepStatus.PENDING,
        accessibility = when {
            accessibilityGranted -> StepStatus.GRANTED
            accessibilityDenied -> StepStatus.DENIED
            else -> StepStatus.PENDING
        },
        screenCapture = when {
            screenCaptureGranted -> StepStatus.GRANTED
            screenCaptureDenied -> StepStatus.DENIED
            else -> StepStatus.PENDING
        },
    )
    val action = nextAction(state, cameraDeniedFlag)
    val captureActive = action == PermissionAction.START_CAPTURE

    if (captureActive) {
        LaunchedEffect(effectivePipeline) {
            effectivePipeline.restore(answerState)
            effectivePipeline.state.collect { answerState = it }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = if (captureActive) "Real-Time MCQ Solver" else "MCQ Solver",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (captureActive) {
                CapturingChip()
                Spacer(modifier = Modifier.width(16.dp))
            }
            IconButton(onClick = { showServerSettings = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Server settings",
                )
            }
        }

        when {
            cameraGranted && captureActive -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    McqCaptureLayout(
                        landscape = isLandscape(maxWidth, maxHeight),
                        captureArea = { modifier ->
                            Box(modifier = modifier) {
                                CameraPreview(
                                    modifier = Modifier.fillMaxSize().testTag(CAMERA_PREVIEW_TAG),
                                    onFrame = { bitmap ->
                                        Log.d("McqSolverScreen", "frame received ${bitmap.width}x${bitmap.height}")
                                        captureScope.launch { effectivePipeline.onFrame(bitmap) }
                                    },
                                )
                                Text(
                                    text = "Align the question within the frame",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(vertical = 8.dp),
                                )
                            }
                        },
                        answerArea = { modifier ->
                            AnswerPanel(
                                state = answerState,
                                modifier = modifier,
                            )
                        },
                    )
                }
            }
            cameraGranted -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    CameraPreview(modifier = Modifier.fillMaxSize().testTag(CAMERA_PREVIEW_TAG))
                }
            }
        }

        if (!captureActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (action) {
                    PermissionAction.REQUEST_CAMERA -> PermissionStep(
                        icon = Icons.Filled.CameraAlt,
                        title = "Camera access",
                        description = "We need camera access to capture the question on your exam paper in real time.",
                        buttonText = "Allow camera access",
                        granted = false,
                        onButtonClick = { launchCameraRequest(Manifest.permission.CAMERA) },
                    )
                    PermissionAction.SHOW_CAMERA_DENIED -> DeniedCard(
                        message = "Camera permission denied",
                        description = "You have denied the camera permission. Grant it to capture questions.",
                        retryText = "Retry",
                        onRetry = { launchCameraRequest(Manifest.permission.CAMERA) },
                    )
                    PermissionAction.REQUEST_ACCESSIBILITY -> PermissionStep(
                        icon = Icons.Filled.Visibility,
                        title = "Accessibility Service",
                        description = "The app observes your screen so it can detect the visible question.",
                        buttonText = "Open Settings",
                        granted = false,
                        onButtonClick = {
                            settingsOpened = true
                            settingsLauncher.launch(accessibilitySettingsIntent())
                        },
                    )
                    PermissionAction.SHOW_STEP_DENIED -> {
                        if (state.accessibility == StepStatus.DENIED) {
                            DeniedCard(
                                message = "Accessibility service not enabled",
                                description = "Enable the accessibility service in Settings to continue.",
                                retryText = "Try again",
                                onRetry = {
                                    settingsOpened = true
                                    settingsLauncher.launch(accessibilitySettingsIntent())
                                },
                            )
                        } else {
                            DeniedCard(
                                message = "Screen capture permission denied",
                                description = "Screen capture permission is required to capture the question.",
                                retryText = "Try again",
                                onRetry = {
                                    context.findActivity()?.let { activity ->
                                        screenCaptureLauncher.launch(
                                            effectiveRequester.createScreenCaptureIntent(activity),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    PermissionAction.REQUEST_SCREEN_CAPTURE -> PermissionStep(
                        icon = Icons.Filled.Videocam,
                        title = "Screen Capture",
                        description = "The app captures the visible question so it can solve it.",
                        buttonText = "Start screen capture",
                        granted = false,
                        onButtonClick = {
                            context.findActivity()?.let { activity ->
                                screenCaptureLauncher.launch(
                                    effectiveRequester.createScreenCaptureIntent(activity),
                                )
                            }
                        },
                    )
                    PermissionAction.START_CAPTURE -> Unit
                }
            }
        }
    }

    if (showServerSettings) {
        ServerSettingsDialog(
            settings = effectiveSettings,
            onDismiss = { showServerSettings = false },
        )
    }
}

private fun accessibilitySettingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

@Composable
private fun ServerSettingsDialog(
    settings: McqServerSettings,
    onDismiss: () -> Unit,
) {
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var model by remember { mutableStateOf(settings.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(SERVER_URL_FIELD_TAG),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(MODEL_FIELD_TAG),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (baseUrl.isNotBlank()) settings.baseUrl = baseUrl
                    if (model.isNotBlank()) settings.model = model
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CapturingChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "Capturing…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
