package com.qwadb.app.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.media.Image
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.qwadb.app.R

private const val DefaultConnectPort = 5555

/**
 * 扫码配对：对准被分享的连接二维码，解析出 host:port 后回调 [onResult]。
 * 仅负责取景与解码，实际连接由调用方（设备页）发起。
 */
@Composable
fun QrScanScreen(
    onResult: (host: String, port: Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var handled by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onQrDetected = { text ->
                    if (handled) return@CameraPreview
                    val target = parseConnectTarget(text)
                    if (target == null) {
                        errorText = context.getString(R.string.qr_scan_invalid)
                        return@CameraPreview
                    }
                    handled = true
                    onResult(target.first, target.second)
                },
            )
        } else {
            Text(
                text = stringResource(R.string.qr_scan_permission_message),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.qr_scan_close),
                tint = Color.White,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.qr_scan_desc),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onQrDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer = remember { QrCodeAnalyzer(onQrDetected) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener(
                {
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(ContextCompat.getMainExecutor(ctx), analyzer)
                            }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}

private class QrCodeAnalyzer(
    private val onDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val text = decode(imageProxy)
            if (!text.isNullOrBlank()) {
                onDetected(text)
            }
        } catch (_: Throwable) {
            // 单帧解码失败属正常情况，忽略
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 从 YUV_420_888 帧中提取 Y（亮度）平面并按 [rotationDegrees] 校正方向后解码。
     * 仅使用 android.media.Image 的标准 API，不依赖 CameraX 内部扩展。
     */
    private fun decode(imageProxy: ImageProxy): String? {
        val image = imageProxy.image ?: return null
        val yData = extractLuminance(image) ?: return null
        val rotation = ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360
        val (data, width, height) = rotateLuminance(yData, image.width, image.height, rotation)
        if (width <= 0 || height <= 0) return null
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return runCatching { reader.decodeWithState(binary).text }.getOrNull()
    }

    /** 抽取 Y 平面为紧凑数组（去除 stride 填充）。 */
    private fun extractLuminance(image: Image): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val buffer = plane.buffer
        buffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val out = ByteArray(width * height)
        var rowOffset = 0
        for (row in 0 until height) {
            var offset = rowOffset
            for (col in 0 until width) {
                if (offset < buffer.limit()) {
                    out[row * width + col] = buffer[offset]
                }
                offset += pixelStride
            }
            rowOffset += rowStride
        }
        return out
    }

    private data class Luminance(
        val data: ByteArray,
        val width: Int,
        val height: Int,
    )

    private fun rotateLuminance(
        source: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
    ): Luminance {
        return when (rotation) {
            90 -> {
                val out = ByteArray(source.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[x * height + (height - 1 - y)] = source[y * width + x]
                    }
                }
                Luminance(out, height, width)
            }
            270 -> {
                val out = ByteArray(source.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(width - 1 - x) * height + y] = source[y * width + x]
                    }
                }
                Luminance(out, height, width)
            }
            180 -> {
                val out = ByteArray(source.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(height - 1 - y) * width + (width - 1 - x)] = source[y * width + x]
                    }
                }
                Luminance(out, width, height)
            }
            else -> Luminance(source, width, height)
        }
    }
}

/** 解析二维码内容，支持 `adb connect host:port`、`host:port`、`host`。 */
internal fun parseConnectTarget(raw: String): Pair<String, Int>? {
    var text = raw.trim()
    val prefix = "adb connect"
    if (text.startsWith(prefix, ignoreCase = true)) {
        text = text.substring(prefix.length).trim()
    }
    if (text.startsWith("adb://", ignoreCase = true)) {
        text = text.substring("adb://".length)
    }
    val hostPart = text.substringBefore('/').trim()
    if (hostPart.isBlank()) return null

    val host: String
    val port: Int
    if (':' in hostPart) {
        host = hostPart.substringBefore(':').trim()
        port = hostPart.substringAfter(':').toIntOrNull() ?: DefaultConnectPort
    } else {
        host = hostPart
        port = DefaultConnectPort
    }
    if (host.isBlank() || !host.matches(Regex("[0-9A-Za-z._\\-]+"))) return null
    if (port !in 1..65535) return null
    return host to port
}
