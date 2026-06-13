package com.connect2world.easypaintmixer

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyPaintMixerApp()
        }
    }
}

private enum class CompareMode(val label: String) {
    ImageTarget("Image target"),
    LiveDualPoint("Live dual point")
}

private enum class ActivePoint(val label: String) {
    Target("Target"),
    Mix("Mix")
}

private enum class CameraLens(val label: String, val selector: CameraSelector) {
    Back("Back", CameraSelector.DEFAULT_BACK_CAMERA),
    Front("Front", CameraSelector.DEFAULT_FRONT_CAMERA)
}

private data class CameraOption(
    val id: String,
    val label: String,
    val lensFacing: Int?
)

private enum class CameraPreset(
    val label: String,
    val shortLabel: String,
    val lockAe: Boolean,
    val lockAwb: Boolean,
    val exposureBias: Int
) {
    Stable("Stable session", "Stable", true, true, 0),
    Auto("Auto view", "Auto", false, false, 0),
    DimLight("Dim light", "Dim", false, false, 2)
}

private enum class ReferenceStandard(
    val label: String,
    val shortLabel: String,
    val note: String,
    val closeLimit: Double,
    val usableLimit: Double,
    val farLimit: Double
) {
    Studio("Studio visual", "Studio", "General visual matching", 3.0, 8.0, 16.0),
    Textile("Textile reference", "TXT", "Textile-style tighter visual tolerance", 2.0, 5.0, 10.0),
    AutoPaint("Auto paint reference", "AUTO", "Automotive-style finish matching reference", 1.5, 3.5, 7.0),
    LabSample("Lab sample reference", "LAB", "Screening only, not instrument-grade", 2.5, 6.0, 12.0)
}

private enum class SuggestionMode(val label: String) {
    Lab("Lab"),
    Hsv("HSV"),
    Hsi("HSI"),
    Rgb("RGB")
}

private enum class AppLanguage(val label: String) {
    English("English"),
    Chinese("简体中文")
}

private enum class AppSection {
    Capture,
    Settings
}

private const val SETTINGS_NAME = "easy_paint_mixer_settings"
private const val KEY_CAMERA_PRESET = "camera_preset"
private const val KEY_REFERENCE_STANDARD = "reference_standard"
private const val KEY_SUGGESTION_MODE = "suggestion_mode"
private const val KEY_CAMERA_LENS = "camera_lens"
private const val KEY_CAMERA_ID = "camera_id"
private const val KEY_EXPOSURE_LOCKED = "exposure_locked"
private const val KEY_APP_LANGUAGE = "app_language"
private const val KEY_ONBOARDING_DISMISSED = "onboarding_dismissed"
private val TargetMarkerColor = Color(0xFF2FD47E)
private val MixMarkerColor = Color(0xFFFFB000)

private data class UiStrings(
    val subtitle: String,
    val language: String,
    val stopCamera: String,
    val openCamera: String,
    val back: String,
    val front: String,
    val imageTarget: String,
    val liveDualPoint: String,
    val target: String,
    val mix: String,
    val current: String,
    val referenceImage: String,
    val uploadImage: String,
    val thenTapTarget: String,
    val uploadPhoto: String,
    val liveMix: String,
    val sampleLive: String,
    val cameraStopped: String,
    val cameraPermissionNeeded: String,
    val allowCamera: String,
    val cameraPermissionRationale: String,
    val localPrivacyNote: String,
    val camera: String,
    val cameraDevice: String,
    val exposure: String,
    val exposureLocked: String,
    val exposureAuto: String,
    val ref: String,
    val system: String,
    val reference: String,
    val currentMix: String,
    val targetRgb: String,
    val deltaE: String,
    val nextSuggestion: String,
    val details: String,
    val hideDetails: String,
    val capture: String,
    val compare: String,
    val library: String,
    val settings: String,
    val basicSettings: String,
    val appInfo: String,
    val disclaimerTitle: String,
    val disclaimerText: String,
    val softwareTitle: String,
    val softwareText: String,
    val privacyTitle: String,
    val version: String,
    val creator: String,
    val resetDefaults: String,
    val openSource: String,
    val githubProfile: String,
    val close: String,
    val usable: String,
    val needsCorrection: String,
    val far: String,
    val stable: String,
    val auto: String,
    val dim: String,
    val studioNote: String,
    val textileNote: String,
    val autoPaintNote: String,
    val labSampleNote: String,
    val traceAmount: String,
    val smallAmount: String,
    val moderateAmount: String,
    val topPickBadge: String,
    val backupPickBadge: String,
    val resampleAfterSuggestion: String,
    val suggestionGuidance: String,
    val recommendationScore: String,
    val cameraOff: String,
    val cameraOn: String,
    val onboardingTitle: String,
    val onboardingImagePath: String,
    val onboardingLivePath: String,
    val onboardingDismiss: String
)

private enum class PaintFamily {
    White,
    Black,
    Yellow,
    Blue,
    Red,
    Green,
    Cyan,
    Magenta,
    Gray
}

private data class AdjustmentSuggestion(
    val family: PaintFamily,
    val label: String,
    val color: Color,
    val reason: String,
    val priority: Int
)

private data class PaintDirection(
    val family: PaintFamily,
    val label: String,
    val color: Color
)

private fun AppLanguage.strings(): UiStrings {
    return when (this) {
        AppLanguage.English -> UiStrings(
            subtitle = "Compare target color with your live paint mix.",
            language = "Language",
            stopCamera = "Stop camera",
            openCamera = "Open camera",
            back = "Back",
            front = "Front",
            imageTarget = "Image target",
            liveDualPoint = "Live dual point",
            target = "Target",
            mix = "Mix",
            current = "Current",
            referenceImage = "Reference image",
            uploadImage = "Upload image",
            thenTapTarget = "then tap target",
            uploadPhoto = "Upload / photo",
            liveMix = "Live mix",
            sampleLive = "Sample live",
            cameraStopped = "Camera stopped",
            cameraPermissionNeeded = "Camera permission needed",
            allowCamera = "Allow camera",
            cameraPermissionRationale = "Camera is only used for live color sampling. You can still use uploaded images without it.",
            localPrivacyNote = "Images and camera frames stay on this device. The app does not upload samples.",
            camera = "Camera",
            cameraDevice = "Camera device",
            exposure = "Exposure",
            exposureLocked = "Locked",
            exposureAuto = "Auto",
            ref = "Ref",
            system = "System",
            reference = "Reference",
            currentMix = "Current mix",
            targetRgb = "Target RGB",
            deltaE = "Delta E",
            nextSuggestion = "Suggested additions",
            details = "Details",
            hideDetails = "Hide details",
            capture = "Capture",
            compare = "Compare",
            library = "Library",
            settings = "Settings",
            basicSettings = "Basic settings",
            appInfo = "App info",
            disclaimerTitle = "Disclaimer",
            disclaimerText = "Phone cameras and screens are not calibrated instruments. Easy-Paint-Mixer offers visual mixing guidance only and does not certify paint, textile, reagent, automotive, or lab color compliance.",
            softwareTitle = "Software",
            softwareText = "Open-source field assistant for comparing a target color with a live paint mix and ranking likely adjustment colors.",
            privacyTitle = "Local privacy",
            version = "Version",
            creator = "Creator",
            resetDefaults = "Restore defaults",
            openSource = "Open source",
            githubProfile = "GitHub profile",
            close = "Close",
            usable = "Usable",
            needsCorrection = "Needs correction",
            far = "Far",
            stable = "Stable",
            auto = "Auto",
            dim = "Dim",
            studioNote = "General visual matching",
            textileNote = "Textile-style tighter visual tolerance",
            autoPaintNote = "Automotive-style finish matching reference",
            labSampleNote = "Screening only, not instrument-grade",
            traceAmount = "Trace amount",
            smallAmount = "Small amount",
            moderateAmount = "Moderate amount",
            topPickBadge = "Top pick",
            backupPickBadge = "Alternative",
            resampleAfterSuggestion = "Add lightly, mix, then sample again.",
            suggestionGuidance = "Visual guidance ranked by color distance, not a pigment recipe.",
            recommendationScore = "Score",
            cameraOff = "Off",
            cameraOn = "On",
            onboardingTitle = "Choose a matching workflow",
            onboardingImagePath = "Image target: pick a target color from a photo, then compare it with the live mix.",
            onboardingLivePath = "Live dual point: tap target and current directly in the camera view for fast field mixing.",
            onboardingDismiss = "Got it"
        )

        AppLanguage.Chinese -> UiStrings(
            subtitle = "对比目标色与镜头中的当前调色。",
            language = "语言",
            stopCamera = "停止摄像头",
            openCamera = "打开摄像头",
            back = "后置",
            front = "前置",
            imageTarget = "图片目标",
            liveDualPoint = "实时双点",
            target = "目标",
            mix = "当前",
            current = "当前",
            referenceImage = "目标图片",
            uploadImage = "上传图片",
            thenTapTarget = "然后点选目标色",
            uploadPhoto = "上传 / 拍照",
            liveMix = "实时当前色",
            sampleLive = "取样当前色",
            cameraStopped = "摄像头已停止",
            cameraPermissionNeeded = "需要摄像头权限",
            allowCamera = "允许摄像头",
            cameraPermissionRationale = "摄像头只用于实时取色。拒绝后仍可使用上传图片模式。",
            localPrivacyNote = "图片和摄像头画面只在本机处理，应用不会上传取样内容。",
            camera = "相机",
            cameraDevice = "摄像头",
            exposure = "曝光",
            exposureLocked = "锁定",
            exposureAuto = "自动",
            ref = "参考",
            system = "色系",
            reference = "目标色",
            currentMix = "当前色",
            targetRgb = "目标色 RGB",
            deltaE = "色差",
            nextSuggestion = "推荐添加色",
            details = "细节",
            hideDetails = "收起细节",
            capture = "取色",
            compare = "对比",
            library = "色库",
            settings = "设置",
            basicSettings = "基本设置",
            appInfo = "软件信息",
            disclaimerTitle = "免责声明",
            disclaimerText = "手机摄像头和屏幕不是校准过的检测仪器。Easy-Paint-Mixer 只提供视觉调色辅助，不作为车漆、纺织品、试剂、实验室样本等颜色合规鉴定依据。",
            softwareTitle = "软件说明",
            softwareText = "一个开源现场调色助手，用于对比目标色与实时当前色，并按优先级推荐可能的调整颜色。",
            privacyTitle = "本地隐私",
            version = "版本号",
            creator = "创作者",
            resetDefaults = "恢复默认",
            openSource = "开源主页",
            githubProfile = "GitHub 主页",
            close = "接近",
            usable = "可用",
            needsCorrection = "需调整",
            far = "差距大",
            stable = "稳定",
            auto = "自动",
            dim = "暗光",
            studioNote = "通用视觉匹配参考",
            textileNote = "纺织品风格的较严格视觉容差",
            autoPaintNote = "车漆表面匹配参考",
            labSampleNote = "仅用于筛查，不是仪器级鉴定",
            traceAmount = "微量",
            smallAmount = "少量",
            moderateAmount = "中量",
            topPickBadge = "首选",
            backupPickBadge = "备选",
            resampleAfterSuggestion = "少量加入，混合后再取样。",
            suggestionGuidance = "基于色差的视觉引导，不是严格颜料配方。",
            recommendationScore = "推荐度",
            cameraOff = "关闭",
            cameraOn = "开启",
            onboardingTitle = "选择取色方式",
            onboardingImagePath = "图片目标：从照片点选目标色，再和镜头中的当前调色对比。",
            onboardingLivePath = "实时双点：直接在镜头画面点选目标和当前，适合现场快速调色。",
            onboardingDismiss = "知道了"
        )
    }
}

private fun CompareMode.label(ui: UiStrings): String {
    return when (this) {
        CompareMode.ImageTarget -> ui.imageTarget
        CompareMode.LiveDualPoint -> ui.liveDualPoint
    }
}

private fun ActivePoint.label(ui: UiStrings): String {
    return when (this) {
        ActivePoint.Target -> ui.target
        ActivePoint.Mix -> ui.mix
    }
}

private fun CameraLens.label(ui: UiStrings): String {
    return when (this) {
        CameraLens.Back -> ui.back
        CameraLens.Front -> ui.front
    }
}

private fun loadCameraOptions(context: Context, ui: UiStrings): List<CameraOption> {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val options = runCatching {
        manager.cameraIdList.map { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val facingLabel = when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> ui.front
                CameraCharacteristics.LENS_FACING_BACK -> ui.back
                else -> ui.camera
            }
            CameraOption(id = id, label = "$facingLabel $id", lensFacing = facing)
        }
    }.getOrDefault(emptyList())
    return options.ifEmpty {
        listOf(CameraOption(id = "0", label = "${ui.back} 0", lensFacing = CameraCharacteristics.LENS_FACING_BACK))
    }
}

private fun CameraPreset.shortLabel(ui: UiStrings): String {
    return when (this) {
        CameraPreset.Stable -> ui.stable
        CameraPreset.Auto -> ui.auto
        CameraPreset.DimLight -> ui.dim
    }
}

private fun ReferenceStandard.note(ui: UiStrings): String {
    return when (this) {
        ReferenceStandard.Studio -> ui.studioNote
        ReferenceStandard.Textile -> ui.textileNote
        ReferenceStandard.AutoPaint -> ui.autoPaintNote
        ReferenceStandard.LabSample -> ui.labSampleNote
    }
}

@Composable
private fun EasyPaintMixerApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF285EA8),
            secondary = Color(0xFFD7A83D),
            tertiary = Color(0xFFD64545),
            background = Color(0xFFFAF8F2),
            surface = Color(0xFFFFFCF7),
            onSurface = Color(0xFF202124)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            PaintMixerHome()
        }
    }
}

@Composable
private fun PaintMixerHome() {
    val context = LocalContext.current
    val settings = remember(context) {
        context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
    }
    var mode by rememberSaveable { mutableStateOf(CompareMode.ImageTarget) }
    var activePoint by rememberSaveable { mutableStateOf(ActivePoint.Target) }
    var targetColorArgb by rememberSaveable { mutableStateOf(Color(0xFFD4A04A).toArgb()) }
    var mixColorArgb by rememberSaveable { mutableStateOf(Color(0xFFC48A54).toArgb()) }
    var targetPointX by rememberSaveable { mutableStateOf(0.34f) }
    var targetPointY by rememberSaveable { mutableStateOf(0.42f) }
    var mixPointX by rememberSaveable { mutableStateOf(0.66f) }
    var mixPointY by rememberSaveable { mutableStateOf(0.58f) }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var referencePoint by remember { mutableStateOf<Offset?>(null) }
    var appSection by rememberSaveable { mutableStateOf(AppSection.Capture) }
    var cameraEnabled by rememberSaveable { mutableStateOf(true) }
    var cameraPreset by remember(settings) {
        mutableStateOf(settings.enumSetting(KEY_CAMERA_PRESET, CameraPreset.Stable))
    }
    var selectedCameraId by remember(settings) { mutableStateOf(settings.getString(KEY_CAMERA_ID, null)) }
    var exposureLocked by remember(settings) { mutableStateOf(settings.getBoolean(KEY_EXPOSURE_LOCKED, true)) }
    var appLanguage by remember(settings) {
        mutableStateOf(settings.enumSetting(KEY_APP_LANGUAGE, AppLanguage.English))
    }
    var referenceStandard by remember(settings) {
        mutableStateOf(settings.enumSetting(KEY_REFERENCE_STANDARD, ReferenceStandard.Studio))
    }
    var suggestionMode by remember(settings) {
        mutableStateOf(settings.enumSetting(KEY_SUGGESTION_MODE, SuggestionMode.Lab))
    }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var onboardingDismissed by remember(settings) {
        mutableStateOf(settings.getBoolean(KEY_ONBOARDING_DISMISSED, false))
    }
    val liveFrameSampler = remember { LatestFrameSampler() }
    val configuration = LocalConfiguration.current
    val ui = appLanguage.strings()
    val cameraOptions = remember(context, appLanguage) { loadCameraOptions(context, ui) }
    val selectedCamera = remember(cameraOptions, selectedCameraId) {
        cameraOptions.firstOrNull { it.id == selectedCameraId }
            ?: cameraOptions.firstOrNull { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }
            ?: cameraOptions.first()
    }
    var liveFrameTick by remember { mutableStateOf(0) }
    val targetColor = Color(targetColorArgb)
    val mixColor = Color(mixColorArgb)
    val targetPoint = Offset(targetPointX, targetPointY)
    val mixPoint = Offset(mixPointX, mixPointY)

    LaunchedEffect(cameraPreset, selectedCamera.id, exposureLocked, appLanguage, referenceStandard, suggestionMode, onboardingDismissed) {
        settings.edit()
            .putString(KEY_CAMERA_PRESET, cameraPreset.name)
            .putString(KEY_CAMERA_ID, selectedCamera.id)
            .putBoolean(KEY_EXPOSURE_LOCKED, exposureLocked)
            .putString(KEY_APP_LANGUAGE, appLanguage.name)
            .putString(KEY_REFERENCE_STANDARD, referenceStandard.name)
            .putString(KEY_SUGGESTION_MODE, suggestionMode.name)
            .putBoolean(KEY_ONBOARDING_DISMISSED, onboardingDismissed)
            .apply()
    }

    LaunchedEffect(liveFrameTick, mode, cameraEnabled, targetPoint, mixPoint) {
        if (!cameraEnabled || liveFrameTick == 0) return@LaunchedEffect
        when (mode) {
            CompareMode.ImageTarget -> {
                liveFrameSampler.sampleFromPreview(Offset(0.5f, 0.5f), 0.86f)?.let { mixColorArgb = it.toArgb() }
            }
            CompareMode.LiveDualPoint -> {
                liveFrameSampler.sampleFromPreview(targetPoint, 1.45f)?.let { targetColorArgb = it.toArgb() }
                liveFrameSampler.sampleFromPreview(mixPoint, 1.45f)?.let { mixColorArgb = it.toArgb() }
            }
        }
    }

    val comparison = remember(targetColor, mixColor, referenceStandard, suggestionMode, appLanguage) {
        compareColors(targetColor, mixColor, referenceStandard, suggestionMode, ui)
    }
    val contentScrollState = rememberScrollState()
    val compactVertical = configuration.screenHeightDp < 620

    LaunchedEffect(appSection) {
        contentScrollState.scrollTo(0)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomStatus(
                ui = ui,
                comparison = comparison,
                appSection = appSection,
                compact = compactVertical,
                onCapture = { appSection = AppSection.Capture },
                onSettings = { appSection = AppSection.Settings }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAF8F2))
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val wideCaptureLayout = maxWidth >= 840.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 980.dp)
                    .consumeWindowInsets(padding)
                    .verticalScroll(contentScrollState)
                    .padding(horizontal = 18.dp, vertical = if (compactVertical) 8.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactVertical) 8.dp else 12.dp)
            ) {
                Header(ui = ui, compact = compactVertical)

                when (appSection) {
                    AppSection.Capture -> {
                        @Composable
                        fun CapturePathContent() {
                            when (mode) {
                                CompareMode.ImageTarget -> ImageTargetPath(
                                    ui = ui,
                                    targetColor = targetColor,
                                    mixColor = mixColor,
                                    referenceBitmap = referenceBitmap,
                                    referencePoint = referencePoint,
                                    liveFrameSampler = liveFrameSampler,
                                    cameraPreset = cameraPreset,
                                    camera = selectedCamera,
                                    exposureLocked = exposureLocked,
                                    cameraEnabled = cameraEnabled,
                                    onFrameSampleReady = { liveFrameTick++ },
                                    onReferenceBitmap = {
                                        referenceBitmap = it
                                        referencePoint = null
                                    },
                                    onReferencePoint = { point -> referencePoint = point },
                                    onTargetColor = { targetColorArgb = it.toArgb() },
                                    onMixColor = { mixColorArgb = it.toArgb() }
                                )

                                CompareMode.LiveDualPoint -> LiveDualPointPath(
                                    ui = ui,
                                    activePoint = activePoint,
                                    onActivePointChange = { activePoint = it },
                                    targetPoint = targetPoint,
                                    mixPoint = mixPoint,
                                    onTap = { position, viewAspect ->
                                        if (activePoint == ActivePoint.Target) {
                                            targetPointX = position.x
                                            targetPointY = position.y
                                            liveFrameSampler.sampleFromPreview(position, viewAspect)?.let { targetColorArgb = it.toArgb() }
                                        } else {
                                            mixPointX = position.x
                                            mixPointY = position.y
                                            liveFrameSampler.sampleFromPreview(position, viewAspect)?.let { mixColorArgb = it.toArgb() }
                                        }
                                    },
                                    targetColor = targetColor,
                                    mixColor = mixColor,
                                    liveFrameSampler = liveFrameSampler,
                                    cameraPreset = cameraPreset,
                                    camera = selectedCamera,
                                    exposureLocked = exposureLocked,
                                    cameraEnabled = cameraEnabled,
                                    onFrameSampleReady = { liveFrameTick++ }
                                )
                            }
                        }

                        @Composable
                        fun ComparisonContent(modifier: Modifier = Modifier) {
                            ColorComparisonPanel(
                                modifier = modifier,
                                ui = ui,
                                targetColor = targetColor,
                                mixColor = mixColor,
                                comparison = comparison,
                                cameraPreset = cameraPreset,
                                onCameraPreset = { cameraPreset = it },
                                referenceStandard = referenceStandard,
                                onReferenceStandard = { referenceStandard = it },
                                suggestionMode = suggestionMode,
                                onSuggestionMode = { suggestionMode = it },
                                detailsExpanded = detailsExpanded,
                                onDetailsExpanded = { detailsExpanded = it }
                            )
                        }

                        CameraQuickControls(
                            ui = ui,
                            cameraEnabled = cameraEnabled,
                            onCameraEnabledChange = { cameraEnabled = it },
                            cameraOptions = cameraOptions,
                            selectedCamera = selectedCamera,
                            onCameraChange = { selectedCameraId = it.id },
                            exposureLocked = exposureLocked,
                            onExposureLockedChange = { exposureLocked = it }
                        )

                        TabRow(selectedTabIndex = mode.ordinal) {
                            CompareMode.entries.forEach { item ->
                                Tab(
                                    selected = item == mode,
                                    onClick = { mode = item },
                                    text = { Text(item.label(ui)) }
                                )
                            }
                        }

                        if (!onboardingDismissed) {
                            OnboardingCard(
                                ui = ui,
                                compact = wideCaptureLayout,
                                onDismiss = { onboardingDismissed = true }
                            )
                        }

                        if (wideCaptureLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1.15f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CapturePathContent()
                                }
                                ComparisonContent(Modifier.weight(0.85f))
                            }
                        } else {
                            CapturePathContent()
                            ComparisonContent()
                        }
                    }

                    AppSection.Settings -> SettingsScreen(
                        ui = ui,
                        appLanguage = appLanguage,
                        onAppLanguage = { appLanguage = it },
                        cameraPreset = cameraPreset,
                        onCameraPreset = { cameraPreset = it },
                        referenceStandard = referenceStandard,
                        onReferenceStandard = { referenceStandard = it },
                        suggestionMode = suggestionMode,
                        onSuggestionMode = { suggestionMode = it },
                        onDefaultsReset = {
                            cameraEnabled = true
                            selectedCameraId = cameraOptions
                                .firstOrNull { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }
                                ?.id ?: cameraOptions.first().id
                            exposureLocked = true
                            cameraPreset = CameraPreset.Stable
                            referenceStandard = ReferenceStandard.Studio
                            suggestionMode = SuggestionMode.Lab
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlStrip(
    ui: UiStrings,
    cameraPreset: CameraPreset,
    onCameraPreset: (CameraPreset) -> Unit,
    referenceStandard: ReferenceStandard,
    onReferenceStandard: (ReferenceStandard) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(ui.camera, fontWeight = FontWeight.SemiBold)
            CameraPreset.entries.forEach { preset ->
                SelectChip(
                    label = preset.shortLabel(ui),
                    selected = preset == cameraPreset,
                    onClick = { onCameraPreset(preset) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ui.ref, fontWeight = FontWeight.SemiBold)
            ReferenceStandard.entries.forEach { standard ->
                SelectChip(
                    label = standard.shortLabel,
                    selected = standard == referenceStandard,
                    onClick = { onReferenceStandard(standard) }
                )
            }
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        animationSpec = tween(durationMillis = 160),
        label = "chipBackground"
    )
    val foreground = if (selected) Color.White else Color(0xFF202124)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .border(1.dp, if (selected) background else Color(0xFFD3CCC1), RoundedCornerShape(100.dp))
            .sizeIn(minWidth = 64.dp, minHeight = 42.dp)
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "selected" else "not selected"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = foreground,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun Header(ui: UiStrings, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 3.dp)) {
        Text(
            text = "Easy-Paint-Mixer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (!compact) {
            Text(
                text = ui.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5F6368)
            )
        }
    }
}

@Composable
private fun OnboardingCard(ui: UiStrings, compact: Boolean = false, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFFCF7))
            .border(1.dp, Color(0xFFE1DAD0), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(ui.onboardingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(ui.onboardingLivePath, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(ui.onboardingDismiss)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ui.onboardingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onDismiss) {
                    Text(ui.onboardingDismiss)
                }
            }
            Text(ui.onboardingImagePath, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
            Text(ui.onboardingLivePath, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CameraQuickControls(
    ui: UiStrings,
    cameraEnabled: Boolean,
    onCameraEnabledChange: (Boolean) -> Unit,
    cameraOptions: List<CameraOption>,
    selectedCamera: CameraOption,
    onCameraChange: (CameraOption) -> Unit,
    exposureLocked: Boolean,
    onExposureLockedChange: (Boolean) -> Unit
) {
    var cameraMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CameraTogglePill(
                label = if (cameraEnabled) ui.cameraOn else ui.cameraOff,
                enabled = cameraEnabled,
                onClick = { onCameraEnabledChange(!cameraEnabled) }
            )
            Box {
                OutlinedButton(onClick = { cameraMenuExpanded = true }) {
                    Text(selectedCamera.label)
                }
                DropdownMenu(
                    expanded = cameraMenuExpanded,
                    onDismissRequest = { cameraMenuExpanded = false }
                ) {
                    cameraOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onCameraChange(option)
                                cameraMenuExpanded = false
                            }
                        )
                    }
                }
            }
            SelectChip(
                label = "${ui.exposure}: ${if (exposureLocked) ui.exposureLocked else ui.exposureAuto}",
                selected = exposureLocked,
                onClick = { onExposureLockedChange(!exposureLocked) }
            )
        }
    }
}

@Composable
private fun CameraTogglePill(label: String, enabled: Boolean, onClick: () -> Unit) {
    if (enabled) {
        Button(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = label }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CameraStatusDot(active = true)
                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = label }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CameraStatusDot(active = false)
                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CameraStatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF2FD47E) else Color(0xFFB9B3AA))
    )
}

@Composable
private fun LanguageSwitch(
    ui: UiStrings,
    appLanguage: AppLanguage,
    onAppLanguage: (AppLanguage) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(ui.language, fontWeight = FontWeight.SemiBold)
        AppLanguage.entries.forEach { language ->
            SelectChip(
                label = language.label,
                selected = language == appLanguage,
                onClick = { onAppLanguage(language) }
            )
        }
    }
}

@Composable
private fun ImageTargetPath(
    ui: UiStrings,
    targetColor: Color,
    mixColor: Color,
    referenceBitmap: Bitmap?,
    referencePoint: Offset?,
    liveFrameSampler: LatestFrameSampler,
    cameraPreset: CameraPreset,
    camera: CameraOption,
    exposureLocked: Boolean,
    cameraEnabled: Boolean,
    onFrameSampleReady: () -> Unit,
    onReferenceBitmap: (Bitmap) -> Unit,
    onReferencePoint: (Offset) -> Unit,
    onTargetColor: (Color) -> Unit,
    onMixColor: (Color) -> Unit
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selected ->
            decodeBitmap(context.contentResolver, selected)?.let(onReferenceBitmap)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(ui.referenceImage, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEDE7DD))
                    .border(1.dp, Color(0xFFD3CCC1), RoundedCornerShape(8.dp))
                    .pointerInput(referenceBitmap) {
                        detectTapGestures { tap ->
                            val normalized = Offset(
                                x = (tap.x / size.width).coerceIn(0f, 1f),
                                y = (tap.y / size.height).coerceIn(0f, 1f)
                            )
                            referenceBitmap?.sampleFromFitView(normalized, size.width.toFloat() / size.height.toFloat())?.let { color ->
                                onReferencePoint(normalized)
                                onTargetColor(color)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (referenceBitmap != null) {
                    Image(
                        bitmap = referenceBitmap.asImageBitmap(),
                        contentDescription = "Reference image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    referencePoint?.let { point ->
                        PointMarker(point = point, markerColor = TargetMarkerColor, label = ui.target)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Swatch(color = targetColor, size = 42)
                        Spacer(Modifier.height(8.dp))
                        Text(ui.uploadImage, color = Color(0xFF5F6368))
                        Text(ui.thenTapTarget, color = Color(0xFF5F6368))
                    }
                }
            }
            OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                Text(ui.uploadPhoto)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(ui.liveMix, fontWeight = FontWeight.SemiBold)
            CameraPreviewSurface(
                ui = ui,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f),
                frameSampler = liveFrameSampler,
                cameraPreset = cameraPreset,
                camera = camera,
                exposureLocked = exposureLocked,
                cameraEnabled = cameraEnabled,
                onFrameSampleReady = onFrameSampleReady,
                overlay = {
                    SampleReticle(markerColor = MixMarkerColor, label = ui.current)
                }
            )
            OutlinedButton(
                onClick = {
                    liveFrameSampler.sampleFromPreview(Offset(0.5f, 0.5f), 0.86f)?.let(onMixColor)
                }
            ) {
                Text(ui.sampleLive)
            }
        }
    }
}

@Composable
private fun LiveDualPointPath(
    ui: UiStrings,
    activePoint: ActivePoint,
    onActivePointChange: (ActivePoint) -> Unit,
    targetPoint: Offset,
    mixPoint: Offset,
    onTap: (Offset, Float) -> Unit,
    targetColor: Color,
    mixColor: Color,
    liveFrameSampler: LatestFrameSampler,
    cameraPreset: CameraPreset,
    camera: CameraOption,
    exposureLocked: Boolean,
    cameraEnabled: Boolean,
    onFrameSampleReady: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivePoint.entries.forEach { point ->
                val selected = activePoint == point
                val action = { onActivePointChange(point) }
                if (selected) {
                    Button(onClick = action) { Text(point.label(ui)) }
                } else {
                    OutlinedButton(onClick = action) { Text(point.label(ui)) }
                }
            }
        }

        CameraPreviewSurface(
            ui = ui,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f),
            frameSampler = liveFrameSampler,
            cameraPreset = cameraPreset,
            camera = camera,
            exposureLocked = exposureLocked,
            cameraEnabled = cameraEnabled,
            onTap = onTap,
            onFrameSampleReady = onFrameSampleReady,
            overlay = {
                DualPointOverlay(
                    ui = ui,
                    targetPoint = targetPoint,
                    mixPoint = mixPoint,
                    targetColor = targetColor,
                    mixColor = mixColor
                )
            }
        )
    }
}

@Composable
private fun CameraPreviewSurface(
    ui: UiStrings,
    modifier: Modifier,
    frameSampler: LatestFrameSampler,
    cameraPreset: CameraPreset,
    camera: CameraOption,
    exposureLocked: Boolean,
    cameraEnabled: Boolean,
    onTap: ((Offset, Float) -> Unit)? = null,
    onFrameSampleReady: () -> Unit = {},
    overlay: @Composable () -> Unit
) {
    val cameraPermission = rememberCameraPermission()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF202124))
            .then(
                if (onTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { tap ->
                            onTap(
                                Offset(
                                    x = (tap.x / size.width).coerceIn(0f, 1f),
                                    y = (tap.y / size.height).coerceIn(0f, 1f)
                                ),
                                size.width.toFloat() / size.height.toFloat()
                            )
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!cameraEnabled) {
            Text(ui.cameraStopped, color = Color.White)
        } else if (cameraPermission.granted) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                frameSampler = frameSampler,
                cameraPreset = cameraPreset,
                camera = camera,
                exposureLocked = exposureLocked,
                onFrameSampleReady = onFrameSampleReady
            )
        } else {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(ui.cameraPermissionNeeded, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    ui.cameraPermissionRationale,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = cameraPermission.request) {
                    Text(ui.allowCamera)
                }
            }
        }
        overlay()
    }
}

private data class CameraPermissionState(
    val granted: Boolean,
    val request: () -> Unit
)

@Composable
private fun rememberCameraPermission(): CameraPermissionState {
    val context = LocalContext.current
    val permissionState = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState.value = granted
    }

    return CameraPermissionState(
        granted = permissionState.value,
        request = { launcher.launch(Manifest.permission.CAMERA) }
    )
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    frameSampler: LatestFrameSampler,
    cameraPreset: CameraPreset,
    camera: CameraOption,
    exposureLocked: Boolean,
    onFrameSampleReady: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )

    DisposableEffect(lifecycleOwner, previewView, frameSampler, cameraPreset, camera.id, exposureLocked) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var lastSampleCallbackAt = 0L

        providerFuture.addListener(
            {
                if (disposed) return@addListener
                val provider = providerFuture.get()
                val previewBuilder = Preview.Builder()
                applyCameraPreset(previewBuilder, cameraPreset, exposureLocked)
                val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysisBuilder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                applyCameraPreset(analysisBuilder, cameraPreset, exposureLocked)
                val analysis = analysisBuilder.build().also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { image ->
                            frameSampler.update(image)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastSampleCallbackAt >= 200L) {
                                lastSampleCallbackAt = now
                                mainExecutor.execute {
                                    if (!disposed) onFrameSampleReady()
                                }
                            }
                            image.close()
                        }
                    }

                provider.unbindAll()
                val selector = CameraSelector.Builder()
                    .addCameraFilter { infos ->
                        infos.filter { info -> Camera2CameraInfo.from(info).cameraId == camera.id }
                    }
                    .build()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    analysis
                )
                val exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
                if (exposureRange.lower <= exposureRange.upper) {
                    camera.cameraControl.setExposureCompensationIndex(
                        cameraPreset.exposureBias.coerceIn(exposureRange.lower, exposureRange.upper)
                    )
                }
            },
            mainExecutor
        )

        onDispose {
            disposed = true
            if (providerFuture.isDone) {
                providerFuture.get().unbindAll()
            }
            analysisExecutor.shutdown()
        }
    }
}

private fun applyCameraPreset(builder: Preview.Builder, preset: CameraPreset, exposureLocked: Boolean) {
    Camera2Interop.Extender(builder)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, exposureLocked)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
}

private fun applyCameraPreset(builder: ImageAnalysis.Builder, preset: CameraPreset, exposureLocked: Boolean) {
    Camera2Interop.Extender(builder)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, exposureLocked)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
}

@Composable
private fun SampleReticle(markerColor: Color, label: String) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(82.dp)) {
            drawSampleFrame(
                center = Offset(size.width / 2f, size.height / 2f),
                markerColor = markerColor
            )
        }
        Text(label, color = Color.White, modifier = Modifier.padding(top = 96.dp))
    }
}

@Composable
private fun PointMarker(point: Offset, markerColor: Color, label: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(point.x * size.width, point.y * size.height)
            drawSampleFrame(center = center, markerColor = markerColor)
        }
        MarkerLabel(point = point, label = label, markerColor = markerColor)
    }
}

@Composable
private fun DualPointOverlay(
    ui: UiStrings,
    targetPoint: Offset,
    mixPoint: Offset,
    targetColor: Color,
    mixColor: Color
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun drawPoint(point: Offset, markerColor: Color) {
                val center = Offset(point.x * size.width, point.y * size.height)
                drawSampleFrame(center = center, markerColor = markerColor)
            }
            drawPoint(targetPoint, TargetMarkerColor)
            drawPoint(mixPoint, MixMarkerColor)
        }
        MarkerLabel(point = targetPoint, label = ui.target, markerColor = TargetMarkerColor)
        MarkerLabel(point = mixPoint, label = ui.current, markerColor = MixMarkerColor)
    }
}

@Composable
private fun MarkerLabel(point: Offset, label: String, markerColor: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val labelWidth = 76.dp
        val x = (maxWidth * point.x).coerceIn(6.dp, (maxWidth - labelWidth).coerceAtLeast(6.dp))
        val y = (maxHeight * point.y - 34.dp).coerceIn(6.dp, (maxHeight - 30.dp).coerceAtLeast(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = x, top = y)
                .background(Color(0xCC101418), RoundedCornerShape(5.dp))
                .border(1.dp, markerColor.copy(alpha = 0.85f), RoundedCornerShape(5.dp))
                .semantics { contentDescription = label }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSampleFrame(
    center: Offset,
    markerColor: Color
) {
    val side = 50f
    val topLeft = Offset(center.x - side / 2f, center.y - side / 2f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.2f),
        topLeft = Offset(topLeft.x + 3f, topLeft.y + 3f),
        size = Size(side, side),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 8f)
    )
    drawRoundRect(
        color = markerColor,
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 7f)
    )
}

@Composable
private fun SuggestionModeStrip(
    ui: UiStrings,
    suggestionMode: SuggestionMode,
    onSuggestionMode: (SuggestionMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(ui.system, fontWeight = FontWeight.SemiBold)
        SuggestionMode.entries.forEach { mode ->
            SelectChip(
                label = mode.label,
                selected = mode == suggestionMode,
                onClick = { onSuggestionMode(mode) }
            )
        }
    }
}

@Composable
private fun ColorComparisonPanel(
    modifier: Modifier = Modifier,
    ui: UiStrings,
    targetColor: Color,
    mixColor: Color,
    comparison: ColorComparison,
    cameraPreset: CameraPreset,
    onCameraPreset: (CameraPreset) -> Unit,
    referenceStandard: ReferenceStandard,
    onReferenceStandard: (ReferenceStandard) -> Unit,
    suggestionMode: SuggestionMode,
    onSuggestionMode: (SuggestionMode) -> Unit,
    detailsExpanded: Boolean,
    onDetailsExpanded: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorReadout(ui.reference, targetColor, Modifier.weight(1f))
            ColorReadout(ui.currentMix, mixColor, Modifier.weight(1f))
        }
        Text("${ui.targetRgb} ${targetColor.toRgbString()}", color = Color(0xFF5F6368), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${ui.deltaE} ${"%.1f".format(comparison.deltaE)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(comparison.matchLabel, color = Color(0xFF2D6A4F), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(comparison.hint, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodyMedium)
        if (comparison.suggestions.isNotEmpty()) {
            AdjustmentSuggestions(ui, comparison.suggestions.take(3), comparison.suggestionMode)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${comparison.standard.shortLabel} · ${comparison.suggestionMode.label}",
                color = Color(0xFF5F6368),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = { onDetailsExpanded(!detailsExpanded) }) {
                Text(if (detailsExpanded) ui.hideDetails else ui.details)
            }
        }
        if (detailsExpanded) {
            DetailControls(
                ui = ui,
                cameraPreset = cameraPreset,
                onCameraPreset = onCameraPreset,
                referenceStandard = referenceStandard,
                onReferenceStandard = onReferenceStandard,
                suggestionMode = suggestionMode,
                onSuggestionMode = onSuggestionMode
            )
            Text("${comparison.standard.shortLabel} · ${comparison.standard.note(ui)}", color = Color(0xFF5F6368))
        }
    }
}

@Composable
private fun DetailControls(
    ui: UiStrings,
    cameraPreset: CameraPreset,
    onCameraPreset: (CameraPreset) -> Unit,
    referenceStandard: ReferenceStandard,
    onReferenceStandard: (ReferenceStandard) -> Unit,
    suggestionMode: SuggestionMode,
    onSuggestionMode: (SuggestionMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlStrip(
            ui = ui,
            cameraPreset = cameraPreset,
            onCameraPreset = onCameraPreset,
            referenceStandard = referenceStandard,
            onReferenceStandard = onReferenceStandard
        )
        SuggestionModeStrip(
            ui = ui,
            suggestionMode = suggestionMode,
            onSuggestionMode = onSuggestionMode
        )
    }
}

@Composable
private fun SettingsScreen(
    ui: UiStrings,
    appLanguage: AppLanguage,
    onAppLanguage: (AppLanguage) -> Unit,
    cameraPreset: CameraPreset,
    onCameraPreset: (CameraPreset) -> Unit,
    referenceStandard: ReferenceStandard,
    onReferenceStandard: (ReferenceStandard) -> Unit,
    suggestionMode: SuggestionMode,
    onSuggestionMode: (SuggestionMode) -> Unit,
    onDefaultsReset: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(ui.basicSettings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onDefaultsReset) {
                Text(ui.resetDefaults)
            }
        }
        LanguageSwitch(
            ui = ui,
            appLanguage = appLanguage,
            onAppLanguage = onAppLanguage
        )
        ControlStrip(
            ui = ui,
            cameraPreset = cameraPreset,
            onCameraPreset = onCameraPreset,
            referenceStandard = referenceStandard,
            onReferenceStandard = onReferenceStandard
        )
        SuggestionModeStrip(
            ui = ui,
            suggestionMode = suggestionMode,
            onSuggestionMode = onSuggestionMode
        )
        SettingsDivider()
        Text(ui.appInfo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFAF8F2))
                .border(1.dp, Color(0xFFE7E1D8), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfoBlock(title = ui.disclaimerTitle, body = ui.disclaimerText)
            InfoBlock(title = ui.privacyTitle, body = ui.localPrivacyNote)
            InfoBlock(title = ui.softwareTitle, body = ui.softwareText)
            InfoRow(label = ui.version, value = "0.1.0-dev")
            InfoRow(label = ui.creator, value = "Andrew Yang / Connect2world")
        }
        GitHubLinkRow(
            label = ui.openSource,
            value = ui.githubProfile,
            onClick = { uriHandler.openUri("https://github.com/Andrew-AI-Kitchen") }
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE7E1D8))
    )
}

@Composable
private fun InfoBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF5F6368))
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GitHubLinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE1DAD0), RoundedCornerShape(8.dp))
            .semantics { contentDescription = "$label $value" }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF5F6368))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF202124)),
                contentAlignment = Alignment.Center
            ) {
                Text("GH", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AdjustmentSuggestions(ui: UiStrings, suggestions: List<AdjustmentSuggestion>, suggestionMode: SuggestionMode) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${ui.nextSuggestion} · ${suggestionMode.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(ui.suggestionGuidance, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
        suggestions.forEachIndexed { index, suggestion ->
            SuggestionCard(ui = ui, suggestion = suggestion, primary = index == 0)
        }
    }
}

@Composable
private fun SuggestionCard(ui: UiStrings, suggestion: AdjustmentSuggestion, primary: Boolean) {
    val score = suggestion.recommendationScore()
    val cardBackground by animateColorAsState(
        targetValue = if (primary) Color(0xFFF3FAF7) else Color(0xFFFFFCF7),
        animationSpec = tween(durationMillis = 180),
        label = "suggestionBackground"
    )
    val cardBorder by animateColorAsState(
        targetValue = if (primary) Color(0xFF7FC9AE) else Color(0xFFE1DAD0),
        animationSpec = tween(durationMillis = 180),
        label = "suggestionBorder"
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cardBackground)
            .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = "${suggestion.label}, ${ui.recommendationScore} $score/100, ${suggestion.amountLabel(ui)}, ${suggestion.reason}"
            }
            .padding(if (primary) 12.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Swatch(color = suggestion.color, size = if (primary) 30 else 24)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        suggestion.label,
                        style = if (primary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (primary) ui.topPickBadge else ui.backupPickBadge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (primary) Color(0xFFE0F1EA) else Color(0xFFF4EFE7))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = if (primary) Color(0xFF0D6B4C) else Color(0xFF6D6255),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "${suggestion.amountLabel(ui)} · ${suggestion.reason}",
                    color = Color(0xFF5F6368),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "$score",
                color = if (primary) Color(0xFF0D6B4C) else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (primary) {
            Text(ui.resampleAfterSuggestion, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (primary) 6.dp else 4.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0xFFE7E1D8))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score / 100f)
                    .height(if (primary) 6.dp else 4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(suggestion.color)
            )
        }
    }
}

@Composable
private fun ColorReadout(title: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "$title ${color.toHex()} RGB ${color.toRgbString()}"
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Swatch(color = color, size = 38)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(color.toHex(), color = Color(0xFF5F6368), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Swatch(color: Color, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color(0x55202124), CircleShape)
    )
}

@Composable
private fun BottomStatus(
    ui: UiStrings,
    comparison: ColorComparison,
    appSection: AppSection,
    compact: Boolean,
    onCapture: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.dp, Color(0xFFE7E1D8))
            .navigationBarsPadding()
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            label = ui.capture,
            selected = appSection == AppSection.Capture,
            compact = compact,
            onClick = onCapture
        )
        MatchStatusChip(ui = ui, comparison = comparison, compact = compact)
        BottomNavItem(
            label = ui.settings,
            selected = appSection == AppSection.Settings,
            compact = compact,
            onClick = onSettings
        )
    }
}

@Composable
private fun BottomNavItem(label: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) Color(0xFFF4EFE7) else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "bottomNavBackground"
    )
    Box(
        modifier = Modifier
            .width(if (compact) 78.dp else 88.dp)
            .sizeIn(minHeight = if (compact) 40.dp else 48.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = if (selected) Color(0xFF202124) else Color(0xFF5F6368)
        )
    }
}

@Composable
private fun MatchStatusChip(ui: UiStrings, comparison: ColorComparison, compact: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0xFFF6FBF8))
            .border(1.dp, Color(0xFFD8EADF), RoundedCornerShape(100.dp))
            .semantics { contentDescription = "${ui.compare} ${comparison.matchLabel}" }
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFF2D6A4F))
        )
        Text(
            "${ui.compare} ${comparison.matchLabel}",
            color = Color(0xFF2D6A4F),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class ColorComparison(
    val deltaE: Double,
    val matchLabel: String,
    val hint: String,
    val standard: ReferenceStandard,
    val suggestionMode: SuggestionMode,
    val suggestions: List<AdjustmentSuggestion>
)

private fun compareColors(
    target: Color,
    mix: Color,
    standard: ReferenceStandard,
    suggestionMode: SuggestionMode,
    ui: UiStrings
): ColorComparison {
    val targetLab = target.toLab()
    val mixLab = mix.toLab()
    val deltaE = sqrt(
        (targetLab.l - mixLab.l).pow(2) +
            (targetLab.a - mixLab.a).pow(2) +
            (targetLab.b - mixLab.b).pow(2)
    )
    val matchLabel = when {
        deltaE < standard.closeLimit -> ui.close
        deltaE < standard.usableLimit -> ui.usable
        deltaE < standard.farLimit -> ui.needsCorrection
        else -> ui.far
    }
    val lightness = when {
        mixLab.l < targetLab.l - 2.0 -> ui.lightnessWord(lighter = true)
        mixLab.l > targetLab.l + 2.0 -> ui.lightnessWord(lighter = false)
        else -> null
    }
    val warmth = when {
        mixLab.b < targetLab.b - 2.0 -> ui.warmthWord(warmer = true)
        mixLab.b > targetLab.b + 2.0 -> ui.warmthWord(warmer = false)
        else -> null
    }
    val hint = listOfNotNull(lightness, warmth)
        .takeIf { it.isNotEmpty() }
        ?.let { ui.adjustHint(it) }
        ?: ui.closeHint()
    return ColorComparison(
        deltaE = deltaE,
        matchLabel = matchLabel,
        hint = hint,
        standard = standard,
        suggestionMode = suggestionMode,
        suggestions = recommendAdjustments(target, mix, suggestionMode, ui).take(3)
    )
}

private fun recommendAdjustments(target: Color, mix: Color, suggestionMode: SuggestionMode, ui: UiStrings): List<AdjustmentSuggestion> {
    return when (suggestionMode) {
        SuggestionMode.Lab -> recommendLabAdjustments(target, target.toLab(), mix.toLab(), ui)
        SuggestionMode.Hsv -> recommendHsvAdjustments(target.toHsvModel(), mix.toHsvModel(), ui)
        SuggestionMode.Hsi -> recommendHsiAdjustments(target.toHsiModel(), mix.toHsiModel(), ui)
        SuggestionMode.Rgb -> recommendRgbAdjustments(target, mix, ui)
    }.mergeSuggestionFamilies(ui).sortedByDescending { it.priority }
}

private fun recommendLabAdjustments(target: Color, targetLab: Lab, mixLab: Lab, ui: UiStrings): List<AdjustmentSuggestion> {
    val suggestions = mutableListOf<AdjustmentSuggestion>()
    val lightnessDelta = targetLab.l - mixLab.l
    val redGreenDelta = targetLab.a - mixLab.a
    val yellowBlueDelta = targetLab.b - mixLab.b
    val chromaTarget = sqrt(targetLab.a.pow(2) + targetLab.b.pow(2))
    val chromaMix = sqrt(mixLab.a.pow(2) + mixLab.b.pow(2))
    val chromaDelta = chromaTarget - chromaMix

    if (lightnessDelta > 2.0) {
        suggestions += AdjustmentSuggestion(PaintFamily.White, ui.whiteBase(), Color(0xFFF5F1E8), ui.reasonDarker(), lightnessDelta.toInt())
    } else if (lightnessDelta < -2.0) {
        suggestions += AdjustmentSuggestion(PaintFamily.Black, ui.blackNeutral(), Color(0xFF242424), ui.reasonLighter(), (-lightnessDelta).toInt())
    }

    if (yellowBlueDelta > 2.0) {
        suggestions += AdjustmentSuggestion(PaintFamily.Yellow, ui.yellowOchre(), Color(0xFFD7A83D), ui.reasonTooCool(), yellowBlueDelta.toInt())
    } else if (yellowBlueDelta < -2.0) {
        suggestions += AdjustmentSuggestion(PaintFamily.Blue, ui.blueViolet(), Color(0xFF285EA8), ui.reasonTooWarm(), (-yellowBlueDelta).toInt())
    }

    if (redGreenDelta > 2.0) {
        suggestions += AdjustmentSuggestion(PaintFamily.Red, ui.redMagenta(), Color(0xFFD64545), ui.reasonNeedsRed(), redGreenDelta.toInt())
    } else if (redGreenDelta < -2.0) {
        suggestions += AdjustmentSuggestion(PaintFamily.Green, ui.greenCyan(), Color(0xFF2D8A66), ui.reasonLessRed(), (-redGreenDelta).toInt())
    }

    if (chromaDelta > 4.0) {
        val color = hueToPaintColor(target.toHsvModel().hue, ui)
        suggestions += AdjustmentSuggestion(color.family, color.label, color.color, ui.reasonMuted(), chromaDelta.toInt())
    } else if (chromaDelta < -4.0) {
        suggestions += AdjustmentSuggestion(PaintFamily.Gray, ui.grayComplement(), Color(0xFF8C8A84), ui.reasonTooSaturated(), (-chromaDelta).toInt())
    }

    return suggestions
}

private fun recommendRgbAdjustments(target: Color, mix: Color, ui: UiStrings): List<AdjustmentSuggestion> {
    val suggestions = mutableListOf<AdjustmentSuggestion>()
    val redDelta = target.red - mix.red
    val greenDelta = target.green - mix.green
    val blueDelta = target.blue - mix.blue
    val brightnessDelta = target.relativeBrightness() - mix.relativeBrightness()

    if (redDelta > 0.04f) {
        suggestions += AdjustmentSuggestion(PaintFamily.Red, ui.redWarm(), Color(0xFFD64545), ui.reasonRedLow(), priority(redDelta))
    } else if (redDelta < -0.04f) {
        suggestions += AdjustmentSuggestion(PaintFamily.Cyan, ui.cyanBlue(), Color(0xFF2AA9A1), ui.reasonRedHigh(), priority(-redDelta))
    }

    if (greenDelta > 0.04f) {
        suggestions += AdjustmentSuggestion(PaintFamily.Green, ui.green(), Color(0xFF2D8A66), ui.reasonGreenLow(), priority(greenDelta))
    } else if (greenDelta < -0.04f) {
        suggestions += AdjustmentSuggestion(PaintFamily.Magenta, ui.magentaViolet(), Color(0xFFC04AA0), ui.reasonGreenHigh(), priority(-greenDelta))
    }

    if (blueDelta > 0.04f) {
        suggestions += AdjustmentSuggestion(PaintFamily.Blue, ui.blue(), Color(0xFF285EA8), ui.reasonBlueLow(), priority(blueDelta))
    } else if (blueDelta < -0.04f) {
        suggestions += AdjustmentSuggestion(PaintFamily.Yellow, ui.yellowOchre(), Color(0xFFD7A83D), ui.reasonBlueHigh(), priority(-blueDelta))
    }

    if (brightnessDelta > 0.05f) {
        suggestions += AdjustmentSuggestion(PaintFamily.White, ui.whiteBase(), Color(0xFFF5F1E8), ui.reasonTooDark(), priority(brightnessDelta))
    } else if (brightnessDelta < -0.05f) {
        suggestions += AdjustmentSuggestion(PaintFamily.Black, ui.blackNeutral(), Color(0xFF242424), ui.reasonTooLight(), priority(-brightnessDelta))
    }

    return suggestions
}

private fun recommendHsvAdjustments(target: HsvColor, mix: HsvColor, ui: UiStrings): List<AdjustmentSuggestion> {
    val suggestions = mutableListOf<AdjustmentSuggestion>()
    val hueDelta = signedHueDelta(target.hue, mix.hue)
    val saturationDelta = target.saturation - mix.saturation
    val valueDelta = target.value - mix.value

    if (abs(hueDelta) > 8.0) {
        val color = hueToPaintColor(target.hue, ui)
        suggestions += AdjustmentSuggestion(color.family, color.label, color.color, ui.reasonHueToward(target.hue), abs(hueDelta).toInt())
    }
    if (saturationDelta > 0.05) {
        val color = hueToPaintColor(target.hue, ui)
        suggestions += AdjustmentSuggestion(color.family, color.label, color.color, ui.reasonMuted(), priority(saturationDelta))
    } else if (saturationDelta < -0.05) {
        suggestions += AdjustmentSuggestion(PaintFamily.Gray, ui.grayComplement(), Color(0xFF8C8A84), ui.reasonTooSaturated(), priority(-saturationDelta))
    }
    if (valueDelta > 0.05) {
        suggestions += AdjustmentSuggestion(PaintFamily.White, ui.whiteBase(), Color(0xFFF5F1E8), ui.reasonValueLow(), priority(valueDelta))
    } else if (valueDelta < -0.05) {
        suggestions += AdjustmentSuggestion(PaintFamily.Black, ui.blackNeutral(), Color(0xFF242424), ui.reasonValueHigh(), priority(-valueDelta))
    }

    return suggestions
}

private fun recommendHsiAdjustments(target: HsiColor, mix: HsiColor, ui: UiStrings): List<AdjustmentSuggestion> {
    val suggestions = mutableListOf<AdjustmentSuggestion>()
    val hueDelta = signedHueDelta(target.hue, mix.hue)
    val saturationDelta = target.saturation - mix.saturation
    val intensityDelta = target.intensity - mix.intensity

    if (abs(hueDelta) > 8.0) {
        val color = hueToPaintColor(target.hue, ui)
        suggestions += AdjustmentSuggestion(color.family, color.label, color.color, ui.reasonHueToward(target.hue), abs(hueDelta).toInt())
    }
    if (saturationDelta > 0.05) {
        val color = hueToPaintColor(target.hue, ui)
        suggestions += AdjustmentSuggestion(color.family, color.label, color.color, ui.reasonColorDull(), priority(saturationDelta))
    } else if (saturationDelta < -0.05) {
        suggestions += AdjustmentSuggestion(PaintFamily.Gray, ui.neutralGray(), Color(0xFF8C8A84), ui.reasonTooIntense(), priority(-saturationDelta))
    }
    if (intensityDelta > 0.05) {
        suggestions += AdjustmentSuggestion(PaintFamily.White, ui.whiteBase(), Color(0xFFF5F1E8), ui.reasonIntensityLow(), priority(intensityDelta))
    } else if (intensityDelta < -0.05) {
        suggestions += AdjustmentSuggestion(PaintFamily.Black, ui.blackNeutral(), Color(0xFF242424), ui.reasonIntensityHigh(), priority(-intensityDelta))
    }

    return suggestions
}

private fun List<AdjustmentSuggestion>.mergeSuggestionFamilies(ui: UiStrings): List<AdjustmentSuggestion> {
    val separator = if (ui.isChinese()) "、" else ", "
    return groupBy { it.family }.map { (_, familySuggestions) ->
        val strongest = familySuggestions.maxBy { it.priority }
        val reasons = familySuggestions
            .map { it.reason }
            .distinct()
            .joinToString(separator)
        val combinedPriority = familySuggestions
            .sortedByDescending { it.priority }
            .foldIndexed(0) { index, total, item ->
                total + if (index == 0) item.priority else item.priority / 2
            }
        strongest.copy(
            reason = reasons,
            priority = combinedPriority.coerceAtLeast(strongest.priority)
        )
    }
}

private data class Lab(val l: Double, val a: Double, val b: Double)
private data class HsvColor(val hue: Double, val saturation: Double, val value: Double)
private data class HsiColor(val hue: Double, val saturation: Double, val intensity: Double)

private fun Color.toLab(): Lab {
    fun pivotRgb(channel: Double): Double {
        return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }

    val r = pivotRgb(red.toDouble())
    val g = pivotRgb(green.toDouble())
    val b = pivotRgb(blue.toDouble())

    val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
    val y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.00000
    val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883

    fun pivotXyz(value: Double): Double {
        return if (value > 0.008856) cbrt(value) else (7.787 * value) + (16.0 / 116.0)
    }

    val fx = pivotXyz(x)
    val fy = pivotXyz(y)
    val fz = pivotXyz(z)
    return Lab(
        l = (116.0 * fy) - 16.0,
        a = 500.0 * (fx - fy),
        b = 200.0 * (fy - fz)
    )
}

private fun Color.toHsvModel(): HsvColor {
    val r = red.toDouble()
    val g = green.toDouble()
    val b = blue.toDouble()
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0.0 -> 0.0
        max == r -> 60.0 * (((g - b) / delta) % 6.0)
        max == g -> 60.0 * (((b - r) / delta) + 2.0)
        else -> 60.0 * (((r - g) / delta) + 4.0)
    }.normalizeHue()
    val saturation = if (max == 0.0) 0.0 else delta / max
    return HsvColor(hue = hue, saturation = saturation, value = max)
}

private fun Color.toHsiModel(): HsiColor {
    val r = red.toDouble()
    val g = green.toDouble()
    val b = blue.toDouble()
    val intensity = (r + g + b) / 3.0
    val min = minOf(r, g, b)
    val saturation = if (intensity == 0.0) 0.0 else 1.0 - (min / intensity)
    val denominator = sqrt((r - g).pow(2) + ((r - b) * (g - b)))
    val ratio = if (denominator == 0.0) 1.0 else (((r - g) + (r - b)) / (2.0 * denominator)).coerceIn(-1.0, 1.0)
    val angle = acos(ratio) * 180.0 / PI
    val hue = if (b <= g) angle else 360.0 - angle
    return HsiColor(hue = hue.normalizeHue(), saturation = saturation, intensity = intensity)
}

private fun Color.relativeBrightness(): Float {
    return (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
}

private fun signedHueDelta(targetHue: Double, mixHue: Double): Double {
    val delta = (targetHue - mixHue + 540.0) % 360.0 - 180.0
    return delta
}

private fun Double.normalizeHue(): Double {
    return ((this % 360.0) + 360.0) % 360.0
}

private fun priority(delta: Float): Int = (delta * 100).toInt().coerceAtLeast(1)

private fun priority(delta: Double): Int = (delta * 100).toInt().coerceAtLeast(1)

private fun hueToPaintColor(hue: Double, ui: UiStrings): PaintDirection {
    return when (hue.normalizeHue()) {
        in 0.0..24.0, in 336.0..360.0 -> PaintDirection(PaintFamily.Red, ui.redWarm(), Color(0xFFD64545))
        in 24.0..72.0 -> PaintDirection(PaintFamily.Yellow, ui.yellowOchre(), Color(0xFFD7A83D))
        in 72.0..156.0 -> PaintDirection(PaintFamily.Green, ui.green(), Color(0xFF2D8A66))
        in 156.0..204.0 -> PaintDirection(PaintFamily.Cyan, ui.cyanBlue(), Color(0xFF2AA9A1))
        in 204.0..276.0 -> PaintDirection(PaintFamily.Blue, ui.blueViolet(), Color(0xFF285EA8))
        else -> PaintDirection(PaintFamily.Magenta, ui.magentaViolet(), Color(0xFFC04AA0))
    }
}

private fun Color.toHex(): String {
    fun channel(value: Float) = (value * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(channel(red), channel(green), channel(blue))
}

private fun Color.toRgbString(): String {
    fun channel(value: Float) = (value * 255).toInt().coerceIn(0, 255)
    return "${channel(red)}, ${channel(green)}, ${channel(blue)}"
}

private fun AdjustmentSuggestion.recommendationScore(): Int {
    return (priority * 4).coerceIn(1, 100)
}

private fun AdjustmentSuggestion.amountLabel(ui: UiStrings): String {
    return when (recommendationScore()) {
        in 1..24 -> ui.traceAmount
        in 25..59 -> ui.smallAmount
        else -> ui.moderateAmount
    }
}

private class LatestFrameSampler {
    private val lock = Any()
    private var pixels: IntArray = intArrayOf()
    private var width: Int = 0
    private var height: Int = 0
    private var rotationDegrees: Int = 0

    fun update(image: ImageProxy) {
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val imageWidth = image.width
        val imageHeight = image.height
        val nextPixels = IntArray(imageWidth * imageHeight)

        buffer.rewind()
        for (y in 0 until imageHeight) {
            val rowOffset = y * rowStride
            for (x in 0 until imageWidth) {
                val offset = rowOffset + x * pixelStride
                if (offset + 2 >= buffer.limit()) continue
                val r = buffer.get(offset).toInt() and 0xFF
                val g = buffer.get(offset + 1).toInt() and 0xFF
                val b = buffer.get(offset + 2).toInt() and 0xFF
                nextPixels[y * imageWidth + x] = argb(r, g, b)
            }
        }

        synchronized(lock) {
            pixels = nextPixels
            width = imageWidth
            height = imageHeight
            rotationDegrees = image.imageInfo.rotationDegrees
        }
    }

    fun sampleFromPreview(normalizedViewPoint: Offset, viewAspect: Float, radius: Int = 5): Color? {
        val snapshot: IntArray
        val snapshotWidth: Int
        val snapshotHeight: Int
        val snapshotRotation: Int
        synchronized(lock) {
            if (pixels.isEmpty() || width <= 0 || height <= 0) return null
            snapshot = pixels
            snapshotWidth = width
            snapshotHeight = height
            snapshotRotation = rotationDegrees
        }

        val displayedAspect = if (snapshotRotation == 90 || snapshotRotation == 270) {
            snapshotHeight.toFloat() / snapshotWidth.toFloat()
        } else {
            snapshotWidth.toFloat() / snapshotHeight.toFloat()
        }
        val displayedPoint = mapFitViewPointToContentPoint(
            viewPoint = normalizedViewPoint,
            viewAspect = viewAspect,
            contentAspect = displayedAspect
        ) ?: return null
        val imagePoint = rotateDisplayPointToImagePoint(displayedPoint, snapshotRotation)
        val centerX = (imagePoint.x.coerceIn(0f, 1f) * (snapshotWidth - 1)).toInt()
        val centerY = (imagePoint.y.coerceIn(0f, 1f) * (snapshotHeight - 1)).toInt()
        return averageColor(snapshot, snapshotWidth, snapshotHeight, centerX, centerY, radius)
    }
}

private fun decodeBitmap(contentResolver: ContentResolver, uri: Uri): Bitmap? {
    return contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)?.copy(Bitmap.Config.ARGB_8888, false)
    }
}

private fun Bitmap.sampleFromFitView(normalizedViewPoint: Offset, viewAspect: Float, radius: Int = 4): Color? {
    if (width <= 0 || height <= 0) return null
    val imagePoint = mapFitViewPointToContentPoint(
        viewPoint = normalizedViewPoint,
        viewAspect = viewAspect,
        contentAspect = width.toFloat() / height.toFloat()
    ) ?: return null
    val centerX = (imagePoint.x.coerceIn(0f, 1f) * (width - 1)).toInt()
    val centerY = (imagePoint.y.coerceIn(0f, 1f) * (height - 1)).toInt()
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return averageColor(pixels, width, height, centerX, centerY, radius)
}

private fun mapFitViewPointToContentPoint(
    viewPoint: Offset,
    viewAspect: Float,
    contentAspect: Float
): Offset? {
    if (viewAspect <= 0f || contentAspect <= 0f) return null

    val contentWidth: Float
    val contentHeight: Float
    val left: Float
    val top: Float

    if (contentAspect > viewAspect) {
        contentWidth = 1f
        contentHeight = viewAspect / contentAspect
        left = 0f
        top = (1f - contentHeight) / 2f
    } else {
        contentHeight = 1f
        contentWidth = contentAspect / viewAspect
        left = (1f - contentWidth) / 2f
        top = 0f
    }

    val right = left + contentWidth
    val bottom = top + contentHeight
    if (viewPoint.x < left || viewPoint.x > right || viewPoint.y < top || viewPoint.y > bottom) {
        return null
    }

    return Offset(
        x = ((viewPoint.x - left) / contentWidth).coerceIn(0f, 1f),
        y = ((viewPoint.y - top) / contentHeight).coerceIn(0f, 1f)
    )
}

private fun rotateDisplayPointToImagePoint(displayPoint: Offset, rotationDegrees: Int): Offset {
    return when (((rotationDegrees % 360) + 360) % 360) {
        90 -> Offset(displayPoint.y, 1f - displayPoint.x)
        180 -> Offset(1f - displayPoint.x, 1f - displayPoint.y)
        270 -> Offset(1f - displayPoint.y, displayPoint.x)
        else -> displayPoint
    }
}

private fun averageColor(
    pixels: IntArray,
    width: Int,
    height: Int,
    centerX: Int,
    centerY: Int,
    radius: Int
): Color? {
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L

    val startX = (centerX - radius).coerceAtLeast(0)
    val endX = (centerX + radius).coerceAtMost(width - 1)
    val startY = (centerY - radius).coerceAtLeast(0)
    val endY = (centerY + radius).coerceAtMost(height - 1)

    for (y in startY..endY) {
        for (x in startX..endX) {
            val color = pixels[y * width + x]
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
            count += 1
        }
    }

    if (count == 0L) return null
    return Color(
        red = (red / count) / 255f,
        green = (green / count) / 255f,
        blue = (blue / count) / 255f
    )
}

private fun argb(red: Int, green: Int, blue: Int): Int {
    return (0xFF shl 24) or
        ((red and 0xFF) shl 16) or
        ((green and 0xFF) shl 8) or
        (blue and 0xFF)
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumSetting(
    key: String,
    defaultValue: T
): T {
    val savedValue = getString(key, null) ?: return defaultValue
    return enumValues<T>().firstOrNull { it.name == savedValue } ?: defaultValue
}

private fun UiStrings.adjustHint(parts: List<String>): String {
    return if (language == "语言") {
        "调整：让当前色更${parts.joinToString("、")}。"
    } else {
        parts.joinToString(prefix = "Adjust: make the mix ", separator = " and ", postfix = ".")
    }
}

private fun UiStrings.closeHint(): String {
    return if (language == "语言") {
        "当前色已经比较接近。充分混合后再取样判断。"
    } else {
        "The mix is visually close. Judge again after the paint is fully blended."
    }
}

private fun UiStrings.isChinese(): Boolean = language == "语言"

private fun UiStrings.lightnessWord(lighter: Boolean): String {
    return if (language == "语言") {
        if (lighter) "亮" else "暗"
    } else {
        if (lighter) "lighter" else "darker"
    }
}

private fun UiStrings.warmthWord(warmer: Boolean): String {
    return if (language == "语言") {
        if (warmer) "暖" else "冷"
    } else {
        if (warmer) "warmer" else "cooler"
    }
}

private fun UiStrings.whiteBase() = if (language == "语言") "白 / 浅色基底" else "White / light base"
private fun UiStrings.blackNeutral() = if (language == "语言") "黑 / 深色中性" else "Black / deep neutral"
private fun UiStrings.yellowOchre() = if (language == "语言") "黄 / 赭黄" else "Yellow / ochre"
private fun UiStrings.blueViolet() = if (language == "语言") "蓝 / 冷紫" else "Blue / cool violet"
private fun UiStrings.redMagenta() = if (language == "语言") "红 / 品红" else "Red / magenta"
private fun UiStrings.greenCyan() = if (language == "语言") "绿 / 青" else "Green / cyan"
private fun UiStrings.cleanerChroma() = if (language == "语言") "更高彩度颜色" else "Cleaner chroma color"
private fun UiStrings.grayComplement() = if (language == "语言") "灰 / 互补色" else "Gray / complement"
private fun UiStrings.redWarm() = if (language == "语言") "红 / 暖色调" else "Red / warm tint"
private fun UiStrings.cyanBlue() = if (language == "语言") "青 / 蓝绿" else "Cyan / green-blue"
private fun UiStrings.green() = if (language == "语言") "绿" else "Green"
private fun UiStrings.magentaViolet() = if (language == "语言") "品红 / 红紫" else "Magenta / red-violet"
private fun UiStrings.blue() = if (language == "语言") "蓝" else "Blue"
private fun UiStrings.pureTargetTint() = if (language == "语言") "目标色相高彩度色" else "Pure tint in target hue"
private fun UiStrings.neutralGray() = if (language == "语言") "中性灰 / 互补色" else "Neutral gray / complement"

private fun UiStrings.reasonDarker() = if (language == "语言") "当前样本偏暗" else "current sample is darker"
private fun UiStrings.reasonLighter() = if (language == "语言") "当前样本偏亮" else "current sample is lighter"
private fun UiStrings.reasonTooCool() = if (language == "语言") "当前样本偏冷" else "current sample is too cool"
private fun UiStrings.reasonTooWarm() = if (language == "语言") "当前样本偏暖" else "current sample is too warm"
private fun UiStrings.reasonNeedsRed() = if (language == "语言") "当前样本需要更多红相" else "current sample needs more red"
private fun UiStrings.reasonLessRed() = if (language == "语言") "当前样本红相过多" else "current sample needs less red"
private fun UiStrings.reasonMuted() = if (language == "语言") "当前样本偏灰" else "sample is muted"
private fun UiStrings.reasonTooSaturated() = if (language == "语言") "当前样本彩度过高" else "sample is too saturated"
private fun UiStrings.reasonRedLow() = if (language == "语言") "红色通道偏低" else "red channel is low"
private fun UiStrings.reasonRedHigh() = if (language == "语言") "红色通道偏高" else "red channel is high"
private fun UiStrings.reasonGreenLow() = if (language == "语言") "绿色通道偏低" else "green channel is low"
private fun UiStrings.reasonGreenHigh() = if (language == "语言") "绿色通道偏高" else "green channel is high"
private fun UiStrings.reasonBlueLow() = if (language == "语言") "蓝色通道偏低" else "blue channel is low"
private fun UiStrings.reasonBlueHigh() = if (language == "语言") "蓝色通道偏高" else "blue channel is high"
private fun UiStrings.reasonTooDark() = if (language == "语言") "样本偏暗" else "sample is too dark"
private fun UiStrings.reasonTooLight() = if (language == "语言") "样本偏亮" else "sample is too light"
private fun UiStrings.reasonHueToward(hue: Double) = if (language == "语言") "色相向 ${hue.toInt()} 度靠近" else "move hue toward ${hue.toInt()} deg"
private fun UiStrings.reasonValueLow() = if (language == "语言") "明度偏低" else "value is too low"
private fun UiStrings.reasonValueHigh() = if (language == "语言") "明度偏高" else "value is too high"
private fun UiStrings.reasonColorDull() = if (language == "语言") "颜色偏浊" else "color is dull"
private fun UiStrings.reasonTooIntense() = if (language == "语言") "颜色强度过高" else "color is too intense"
private fun UiStrings.reasonIntensityLow() = if (language == "语言") "强度偏低" else "intensity is too low"
private fun UiStrings.reasonIntensityHigh() = if (language == "语言") "强度偏高" else "intensity is too high"
