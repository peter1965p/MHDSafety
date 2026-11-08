package com.paeffgen.mhdsafety.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.util.Size as PixelSize
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.paeffgen.mhdsafety.R
import com.paeffgen.mhdsafety.domain.Box
import com.paeffgen.mhdsafety.domain.DateImage
import com.paeffgen.mhdsafety.domain.DateParser
import com.paeffgen.mhdsafety.domain.DateVariant
import com.paeffgen.mhdsafety.domain.DateVotes
import com.paeffgen.mhdsafety.domain.ParsedDate
import java.time.LocalDate
import java.util.concurrent.Executors

enum class ScanMode { BARCODE, DATE }

/** So oft hintereinander muss derselbe Barcode erkannt werden, damit Lesefehler nicht durchrutschen. */
private const val STABLE_FRAMES = 2

/** Anfangszoom beim Datum-Scannen. */
private const val DATE_ZOOM = 1.8f

/**
 * Bild für Bild eine andere Aufbereitung. "Punkte verbunden" kommt am häufigsten dran:
 * im Test las ML Kit damit 18 von 18 Punktmatrix-Aufdrucken, roh keinen einzigen.
 * Sauberer Druck klappt mit allen Varianten.
 */
private val DATE_VARIANTS = listOf(
    DateVariant.DOTS, DateVariant.RAW, DateVariant.DOTS, DateVariant.CONTRAST, DateVariant.DOTS, DateVariant.INVERTED,
)

/** Wie viel über dem Rahmen noch mitgelesen wird (Anteil der Rahmenhöhe), für „zu verbrauchen bis“. */
private const val ABOVE_FRAME = 1f

/**
 * Schneidet den Rahmen aus dem Kamerabild, bereitet ihn auf und liest das Datum.
 * Läuft auf dem Analyse-Thread und wartet dort auf die Texterkennung.
 */
private fun readDate(
    proxy: ImageProxy,
    recognizer: TextRecognizer,
    variant: DateVariant,
    viewWidth: Int,
    viewHeight: Int,
): ParsedDate? {
    val rotation = proxy.imageInfo.rotationDegrees
    val frameF = frameRect(ScanMode.DATE, viewWidth.toFloat(), viewHeight.toFloat())
    val frame = Box(frameF.left.toInt(), frameF.top.toInt(), frameF.right.toInt(), frameF.bottom.toInt())
    val extendUp = (frame.height * ABOVE_FRAME).toInt()
    val box = DateImage.uprightBox(proxy.width, proxy.height, rotation, viewWidth, viewHeight, frame, extendUp)
        ?: return null
    val plane = proxy.planes[0]
    val gray = DateImage.uprightCrop(plane.buffer, plane.rowStride, plane.pixelStride, proxy.width, proxy.height, rotation, box)
    val prepared = DateImage.prepare(gray, variant)
    val argb = IntArray(prepared.pixels.size) { i ->
        val v = prepared.pixels[i]
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(argb, prepared.width, prepared.height, Bitmap.Config.ARGB_8888)
    val text = runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))) }.getOrNull()
        ?: return null
    // Nur Text im eigentlichen Rahmen auswerten; was darüber steht, nur für „zu verbrauchen bis“.
    val frameStart = DateImage.rowsAboveFrame(box, frame, extendUp) * 0.8f
    val inFrame = text.textBlocks.flatMap { it.lines }
        .filter { line -> line.boundingBox?.let { it.exactCenterY() >= frameStart } == true }
        .joinToString("\n") { it.text }
    return DateParser.parse(inFrame, LocalDate.now())
        ?.let { it.copy(useBy = DateParser.mentionsUseBy(text.text)) }
}

@Composable
fun ScanScreen(
    mode: ScanMode,
    onBarcode: (String) -> Unit,
    onDate: (ParsedDate) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraScanner(mode, onBarcode, onDate)
        } else if (askedOnce) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Ohne Kamera-Erlaubnis kann nicht gescannt werden.", textAlign = TextAlign.Center)
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 16.dp)) {
                    Text("Erlauben")
                }
            }
        }

        FilledTonalIconButton(
            onClick = onCancel,
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
        ) { Icon(Icons.Default.Close, contentDescription = "Abbrechen") }
    }
}

/** Rahmen, in den man Barcode bzw. Datum halten soll (in Pixeln der Vorschau). */
private fun frameRect(mode: ScanMode, width: Float, height: Float): Rect {
    val w = width * 0.85f
    val h = when (mode) {
        ScanMode.BARCODE -> height * 0.2f
        ScanMode.DATE -> height * 0.14f
    }
    val left = (width - w) / 2
    val top = (height - h) / 2
    return Rect(left, top, left + w, top + h)
}

@Composable
private fun CameraScanner(
    mode: ScanMode,
    onBarcode: (String) -> Unit,
    onDate: (ParsedDate) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val controller = remember { LifecycleCameraController(context) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var torchOn by remember { mutableStateOf(false) }
    var foundDate by remember { mutableStateOf<ParsedDate?>(null) }

    DisposableEffect(mode) {
        val executor = ContextCompat.getMainExecutor(context)
        var lastValue: Any? = null
        var sameCount = 0
        var done = false

        /** Liefert true, sobald derselbe Wert oft genug hintereinander erkannt wurde. */
        fun stable(value: Any?): Boolean {
            if (value == null) {
                sameCount = 0
                return false
            }
            sameCount = if (value == lastValue) sameCount + 1 else 1
            lastValue = value
            return sameCount >= STABLE_FRAMES
        }

        val analyzer = when (mode) {
            ScanMode.BARCODE -> {
                val scanner: BarcodeScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                        .build(),
                )
                MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                    if (done) return@MlKitAnalyzer
                    val code = result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue }
                    if (stable(code)) {
                        done = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBarcode(code!!)
                    }
                } to scanner
            }
            ScanMode.DATE -> {
                val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val votes = DateVotes()
                var frameNo = 0
                // Kleine Schrift braucht Pixel: statt der üblichen 640×480 in Full HD analysieren.
                controller.imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(PixelSize(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build()
                ImageAnalysis.Analyzer { proxy ->
                    proxy.use {
                        if (done) return@Analyzer
                        if (foundDate != null) {
                            // Wartet auf Bestätigung; nach "Nochmal" wieder von vorn zählen.
                            votes.clear()
                            return@Analyzer
                        }
                        val variant = DATE_VARIANTS[frameNo++ % DATE_VARIANTS.size]
                        val parsed = readDate(proxy, recognizer, variant, previewView.width, previewView.height)
                        val sure = votes.add(parsed) ?: return@Analyzer
                        executor.execute {
                            if (done || foundDate != null) return@execute
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            foundDate = sure
                        }
                    }
                } to recognizer
            }
        }

        // Datum: Aufbereitung + Texterkennung dauern, also nicht auf dem Hauptthread.
        val worker = if (mode == ScanMode.DATE) Executors.newSingleThreadExecutor() else null
        controller.setImageAnalysisAnalyzer(worker ?: executor, analyzer.first)
        controller.bindToLifecycle(lifecycleOwner)
        previewView.controller = controller
        if (mode == ScanMode.DATE) {
            // Etwas Zoom: dann kann man weiter weg halten, wo die Kamera noch scharf stellt.
            controller.initializationFuture.addListener({
                val max = controller.zoomState.value?.maxZoomRatio ?: 1f
                controller.setZoomRatio(DATE_ZOOM.coerceAtMost(max))
            }, executor)
        }

        onDispose {
            done = true
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            worker?.shutdown()
            analyzer.second.close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Abgedunkelter Rand mit freiem Rahmen in der Mitte
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            val frame = frameRect(mode, size.width, size.height)
            val corner = CornerRadius(16.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(Color.Transparent, frame.topLeft, frame.size, corner, blendMode = BlendMode.Clear)
            drawRoundRect(
                if (foundDate != null) Color(0xFF66BB6A) else Color.White,
                frame.topLeft,
                Size(frame.width, frame.height),
                corner,
                style = Stroke(3.dp.toPx()),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val date = foundDate
            if (date == null) {
                Text(
                    when (mode) {
                        ScanMode.BARCODE -> "Barcode in den Rahmen halten"
                        ScanMode.DATE -> "Datum in den Rahmen halten"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (mode == ScanMode.DATE) {
                    Text(
                        "Unscharf? Aufs Bild tippen. Blass oder spiegelnd? Licht an oder etwas schräg halten.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Erkannt:", style = MaterialTheme.typography.labelLarge)
                        Text(date.date.formatDe(), style = MaterialTheme.typography.headlineMedium)
                        if (date.useBy) {
                            Text("„Zu verbrauchen bis“ erkannt", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { foundDate = null }, Modifier.weight(1f)) {
                                Text("Nochmal")
                            }
                            Button(onClick = { onDate(date) }, Modifier.weight(1f)) {
                                Text("Übernehmen")
                            }
                        }
                    }
                }
            }
            if (controller.cameraInfo?.hasFlashUnit() != false) {
                FilledTonalIconButton(
                    onClick = {
                        torchOn = !torchOn
                        controller.enableTorch(torchOn)
                    },
                    colors = if (torchOn) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_flashlight),
                        contentDescription = if (torchOn) "Licht aus" else "Licht an",
                    )
                }
            }
        }
    }
}
