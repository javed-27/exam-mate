package com.exammate.ui.theory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.exammate.mcq.CameraPermissionStore
import com.exammate.mcq.SharedPrefsCameraPermissionStore
import com.exammate.mcq.ocr.MlKitOcrService
import com.exammate.mcq.ocr.OcrService
import com.exammate.theory.TheoryCaptureModel
import com.exammate.theory.TheoryCaptureState
import com.exammate.ui.mcq.CAMERA_PREVIEW_TAG
import com.exammate.ui.mcq.CameraPreview
import com.exammate.ui.mcq.DeniedCard
import com.exammate.ui.mcq.PermissionStep

internal const val SHUTTER_BUTTON_TAG = "shutter_button"

@Composable
fun TheorySolverScreen(
    onBack: () -> Unit,
    cameraPermissionStore: CameraPermissionStore? = null,
    isCameraGranted: (() -> Boolean)? = null,
    requestCamera: ((permission: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    ocrService: OcrService? = null,
    model: TheoryCaptureModel? = null,
    initialFrame: Bitmap? = null,
) {
    val context = LocalContext.current
    val effectiveStore = cameraPermissionStore
        ?: remember { SharedPrefsCameraPermissionStore(context) }
    val effectiveIsCameraGranted = isCameraGranted
        ?: remember { { isCameraPermissionGranted(context) } }
    val effectiveOcr = ocrService ?: remember { MlKitOcrService() }

    var cameraGranted by remember { mutableStateOf(effectiveIsCameraGranted()) }
    var latestFrame by remember { mutableStateOf(initialFrame) }
    var preservedOcrText by rememberSaveable { mutableStateOf<String?>(null) }
    var showEnlarged by remember { mutableStateOf(false) }

    val effectiveModel = model ?: remember {
        TheoryCaptureModel(recognize = { effectiveOcr.recognize(latestFrame!!) })
    }
    val captureState by effectiveModel.state.collectAsState(
        initial = TheoryCaptureState.Viewfinder,
    )

    LaunchedEffect(effectiveModel) {
        effectiveModel.state.collect { state ->
            if (state is TheoryCaptureState.Captured) preservedOcrText = state.ocrText
        }
    }

    DisposableEffect(effectiveModel, effectiveOcr) {
        onDispose {
            if (model == null) effectiveModel.close()
            if (ocrService == null) effectiveOcr.close()
        }
    }

    val onRecapture: () -> Unit = {
        preservedOcrText = null
        effectiveModel.recapture()
    }
    val onCameraResult: (Boolean) -> Unit = { granted ->
        if (granted) {
            cameraGranted = true
            effectiveStore.cameraDenied = false
        } else {
            effectiveStore.cameraDenied = true
            onBack()
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

    val restoredText = preservedOcrText
    val effectiveCaptureState = if (
        captureState is TheoryCaptureState.Viewfinder && restoredText != null
    ) {
        TheoryCaptureState.Captured(restoredText)
    } else {
        captureState
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
                text = "Theory Question Solver",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        when {
            !cameraGranted && effectiveStore.cameraDenied -> {
                PermissionColumn(modifier = Modifier.weight(1f)) {
                    DeniedCard(
                        message = "Camera permission denied",
                        description = "You have denied the camera permission. Grant it to capture questions.",
                        retryText = "Retry",
                        onRetry = { launchCameraRequest(Manifest.permission.CAMERA) },
                    )
                }
            }
            !cameraGranted -> {
                PermissionColumn(modifier = Modifier.weight(1f)) {
                    PermissionStep(
                        icon = Icons.Filled.CameraAlt,
                        title = "Camera access",
                        description = "We need camera access to capture the question from your exam paper.",
                        buttonText = "Allow camera access",
                        granted = false,
                        onButtonClick = { launchCameraRequest(Manifest.permission.CAMERA) },
                    )
                }
            }
            else -> {
                when (effectiveCaptureState) {
                    TheoryCaptureState.Viewfinder -> ViewfinderContent(
                        onFrame = { latestFrame = it },
                        shutterEnabled = latestFrame != null,
                        onShutter = { effectiveModel.capture() },
                    )
                    TheoryCaptureState.Capturing,
                    is TheoryCaptureState.Captured,
                    is TheoryCaptureState.OcrFailed -> CapturedContent(
                        state = effectiveCaptureState,
                        image = latestFrame,
                        onEnlarge = { showEnlarged = true },
                        onRecapture = onRecapture,
                    )
                }
            }
        }
    }

    if (showEnlarged) {
        latestFrame?.let { image ->
            EnlargeImageDialog(
                image = image,
                onDismiss = { showEnlarged = false },
            )
        }
    }
}

@Composable
private fun PermissionColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
private fun ViewfinderContent(
    onFrame: (Bitmap) -> Unit,
    shutterEnabled: Boolean,
    onShutter: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize().testTag(CAMERA_PREVIEW_TAG),
            onFrame = onFrame,
        )
        Text(
            text = "Align the question within the frame",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 96.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(vertical = 8.dp),
        )
        FilledIconButton(
            onClick = onShutter,
            enabled = shutterEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(72.dp)
                .testTag(SHUTTER_BUTTON_TAG),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Capture question",
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun CapturedContent(
    state: TheoryCaptureState,
    image: Bitmap?,
    onEnlarge: () -> Unit,
    onRecapture: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        CapturedQuestionCard(
            image = image,
            onEnlarge = onEnlarge,
            onRecapture = onRecapture,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f)
                .padding(vertical = 8.dp),
        )
        TheoryAnswerArea(
            state = state,
            onRetake = onRecapture,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f),
        )
    }
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
