package com.youclipbot.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                YouClipBotApp(context = this)
            }
        }
    }
}

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val sizeMb: Double,
    val durationMs: Long
)

data class ClipCandidate(
    val startMs: Long,
    val endMs: Long,
    val visualScore: Int,
    val hookScore: Int,
    val durationScore: Int,
    val viralScore: Int,
    val facePresence: Int,
    val reasons: List<String>
)

@Composable
fun YouClipBotApp(
    context: Context
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var videoInfo by remember {
        mutableStateOf<VideoInfo?>(null)
    }

    var clipCandidates by remember {
        mutableStateOf<List<ClipCandidate>>(emptyList())
    }

    var analyzing by remember {
        mutableStateOf(false)
    }

    var analysisStatus by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val videoPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Beberapa file picker hanya memberi izin sementara.
                    }

                    videoInfo = getVideoInfo(
                        context = context,
                        uri = uri
                    )

                    clipCandidates = emptyList()
                    analysisStatus = ""
                    errorMessage = null

                } catch (exception: Exception) {
                    errorMessage =
                        "Gagal membaca video: " +
                            (
                                exception.message
                                    ?: "Kesalahan tidak diketahui"
                                )
                }
            }
        }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "YouClipBot v2.3",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text =
                    "Pilih video panjang dan analisis maksimal lima " +
                        "kandidat clip berdasarkan durasi, kualitas visual, " +
                        "perubahan gambar, dan keberadaan wajah."
            )

            Text(
                text =
                    "Estimated Viral Score adalah estimasi teknis awal. " +
                        "Skor ini bukan jaminan bahwa video akan viral."
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !analyzing,
                onClick = {
                    videoPicker.launch(arrayOf("video/*"))
                }
            ) {
                Text(
                    text =
                        if (videoInfo == null) {
                            "Pilih Video"
                        } else {
                            "Ganti Video"
                        }
                )
            }

            videoInfo?.let { video ->

                HorizontalDivider()

                Text(
                    text = "Video terpilih",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Nama: ${video.name}"
                )

                Text(
                    text = "Ukuran: %.1f MB".format(video.sizeMb)
                )

                Text(
                    text = "Durasi: ${formatDuration(video.durationMs)}"
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !analyzing,
                    onClick = {
                        scope.launch {
                            analyzing = true
                            errorMessage = null
                            clipCandidates = emptyList()

                            analysisStatus =
                                "Menyiapkan analisis video..."

                            try {
                                val results: List<ClipCandidate> =
                                    analyzeVideo(
                                        context = context,
                                        video = video,
                                        onProgress = { message: String ->
                                            analysisStatus = message
                                        }
                                    )

                                clipCandidates = results

                                analysisStatus =
                                    if (results.isNotEmpty()) {
                                        "Analisis selesai. " +
                                            "${results.size} kandidat ditemukan."
                                    } else {
                                        "Analisis selesai, tetapi tidak ada " +
                                            "kandidat yang dapat dibuat."
                                    }

                            } catch (exception: Exception) {
                                errorMessage =
                                    "Analisis gagal: " +
                                        (
                                            exception.message
                                                ?: "Kesalahan tidak diketahui"
                                            )

                                analysisStatus = ""

                            } finally {
                                analyzing = false
                            }
                        }
                    }
                ) {
                    Text(
                        text =
                            if (analyzing) {
                                "Sedang Menganalisis..."
                            } else {
                                "Analisis Video"
                            }
                    )
                }
            }

            if (analyzing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (analysisStatus.isNotBlank()) {
                Text(text = analysisStatus)
            }

            errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Terjadi kesalahan",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (clipCandidates.isNotEmpty()) {
                HorizontalDivider()

                Text(
                    text = "Kandidat clip terbaik",
                    style = MaterialTheme.typography.titleLarge
                )

                clipCandidates.forEachIndexed { index, candidate ->

                    ClipCandidateCard(
                        rank = index + 1,
                        candidate = candidate
                    )
                }
            }
        }
    }
}

@Composable
fun ClipCandidateCard(
    rank: Int,
    candidate: ClipCandidate
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Kandidat #$rank",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text =
                    "Waktu: ${formatDuration(candidate.startMs)} sampai " +
                        formatDuration(candidate.endMs)
            )

            Text(
                text =
                    "Durasi clip: " +
                        formatDuration(
                            candidate.endMs - candidate.startMs
                        )
            )

            Text(
                text = "Estimated Viral Score: ${candidate.viralScore}/100",
                style = MaterialTheme.typography.titleMedium
            )

            LinearProgressIndicator(
                progress = {
                    candidate.viralScore.coerceIn(0, 100) / 100f
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Visual Score: ${candidate.visualScore}/100"
            )

            Text(
                text = "Hook Score: ${candidate.hookScore}/100"
            )

            Text(
                text = "Duration Score: ${candidate.durationScore}/100"
            )

            Text(
                text = "Face Presence: ${candidate.facePresence}%"
            )

            if (candidate.reasons.isNotEmpty()) {
                Text(
                    text = "Alasan pemilihan:",
                    style = MaterialTheme.typography.titleSmall
                )

                candidate.reasons.forEach { reason ->
                    Text(text = "• $reason")
                }
            }
        }
    }
}

fun getVideoInfo(
    context: Context,
    uri: Uri
): VideoInfo {
    var fileName = "video"
    var fileSizeBytes = 0L

    context.contentResolver.query(
        uri,
        arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        ),
        null,
        null,
        null
    )?.use { cursor ->

        if (cursor.moveToFirst()) {
            val nameIndex =
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            val sizeIndex =
                cursor.getColumnIndex(OpenableColumns.SIZE)

            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                fileName = cursor.getString(nameIndex)
            }

            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                fileSizeBytes = cursor.getLong(sizeIndex)
            }
        }
    }

    val retriever = MediaMetadataRetriever()

    val durationMs: Long =
        try {
            retriever.setDataSource(context, uri)

            retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

        } finally {
            retriever.release()
        }

    if (durationMs <= 0L) {
        throw IllegalArgumentException(
            "Durasi video tidak dapat dibaca."
        )
    }

    val sizeMb =
        fileSizeBytes.toDouble() / (1024.0 * 1024.0)

    return VideoInfo(
        uri = uri,
        name = fileName,
        sizeMb = sizeMb,
        durationMs = durationMs
    )
}

fun formatDuration(
    durationMs: Long
): String {
    val safeDuration = max(0L, durationMs)

    val totalSeconds = safeDuration / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%02d:%02d:%02d".format(
            hours,
            minutes,
            seconds
        )
    } else {
        "%02d:%02d".format(
            minutes,
            seconds
        )
    }
}

suspend fun analyzeVideo(
    context: Context,
    video: VideoInfo,
    onProgress: (String) -> Unit
): List<ClipCandidate> = withContext(Dispatchers.Default) {

    if (video.durationMs <= 0L) {
        throw IllegalArgumentException(
            "Durasi video tidak valid."
        )
    }

    onProgress("Membuka video...")

    val retriever = MediaMetadataRetriever()

    try {
        retriever.setDataSource(context, video.uri)

        val preferredClipDurationMs =
            determineClipDuration(video.durationMs)

        val candidateStarts =
            buildCandidateStartTimes(
                videoDurationMs = video.durationMs,
                clipDurationMs = preferredClipDurationMs
            )

        val detectorOptions =
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_FAST
                )
                .setLandmarkMode(
                    FaceDetectorOptions.LANDMARK_MODE_NONE
                )
                .setClassificationMode(
                    FaceDetectorOptions.CLASSIFICATION_MODE_NONE
                )
                .enableTracking()
                .build()

        val faceDetector =
            FaceDetection.getClient(detectorOptions)

        try {
            val results = mutableListOf<ClipCandidate>()

            candidateStarts.forEachIndexed { index, startMs ->

                onProgress(
                    "Menganalisis bagian ${index + 1} " +
                        "dari ${candidateStarts.size}..."
                )

                val endMs =
                    min(
                        startMs + preferredClipDurationMs,
                        video.durationMs
                    )

                val candidate =
                    analyzeCandidateWindow(
                        retriever = retriever,
                        startMs = startMs,
                        endMs = endMs,
                        videoDurationMs = video.durationMs,
                        faceDetector = faceDetector
                    )

                results.add(candidate)
            }

            onProgress("Mengurutkan kandidat terbaik...")

            return@withContext results
                .sortedWith(
                    compareByDescending<ClipCandidate> {
                        it.viralScore
                    }.thenByDescending {
                        it.visualScore
                    }.thenByDescending {
                        it.facePresence
                    }
                )
                .distinctBy { candidate ->
                    candidate.startMs / 5000L
                }
                .take(5)

        } finally {
            faceDetector.close()
        }

    } finally {
        retriever.release()
    }
}

fun determineClipDuration(
    videoDurationMs: Long
): Long {
    return when {
        videoDurationMs >= 10L * 60L * 1000L ->
            45_000L

        videoDurationMs >= 3L * 60L * 1000L ->
            35_000L

        videoDurationMs >= 60_000L ->
            30_000L

        else ->
            min(videoDurationMs, 20_000L)
    }
}

fun buildCandidateStartTimes(
    videoDurationMs: Long,
    clipDurationMs: Long
): List<Long> {
    if (videoDurationMs <= clipDurationMs) {
        return listOf(0L)
    }

    val maximumStart =
        max(0L, videoDurationMs - clipDurationMs)

    val desiredCandidateCount =
        when {
            videoDurationMs >= 20L * 60L * 1000L -> 15
            videoDurationMs >= 10L * 60L * 1000L -> 12
            videoDurationMs >= 3L * 60L * 1000L -> 10
            else -> 6
        }

    if (desiredCandidateCount <= 1) {
        return listOf(0L)
    }

    val interval =
        maximumStart / (desiredCandidateCount - 1)

    return (0 until desiredCandidateCount)
        .map { index ->
            min(
                index * interval,
                maximumStart
            )
        }
        .distinct()
}

suspend fun analyzeCandidateWindow(
    retriever: MediaMetadataRetriever,
    startMs: Long,
    endMs: Long,
    videoDurationMs: Long,
    faceDetector: com.google.mlkit.vision.face.FaceDetector
): ClipCandidate {
    val sampleCount = 5

    val windowDuration =
        max(1L, endMs - startMs)

    val frames = mutableListOf<Bitmap>()
    var detectedFaceFrames = 0

    try {
        for (sampleIndex in 0 until sampleCount) {
            val relativePosition =
                if (sampleCount == 1) {
                    0L
                } else {
                    windowDuration * sampleIndex /
                        (sampleCount - 1)
                }

            val frameTimeMs =
                min(
                    startMs + relativePosition,
                    max(0L, videoDurationMs - 1L)
                )

            val frame =
                retriever.getFrameAtTime(
                    frameTimeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

            if (frame != null) {
                frames.add(frame)

                try {
                    val inputImage =
                        InputImage.fromBitmap(frame, 0)

                    val faces =
                        faceDetector.process(inputImage).await()

                    if (faces.isNotEmpty()) {
                        detectedFaceFrames += 1
                    }
                } catch (_: Exception) {
                    // Analisis visual tetap dilanjutkan jika satu frame gagal.
                }
            }
        }

        val facePresence =
            if (frames.isNotEmpty()) {
                (
                    detectedFaceFrames.toDouble() /
                        frames.size.toDouble() *
                        100.0
                    ).toInt()
            } else {
                0
            }

        val brightnessValues =
            frames.map { bitmap ->
                calculateAverageBrightness(bitmap)
            }

        val averageBrightness =
            if (brightnessValues.isNotEmpty()) {
                brightnessValues.average()
            } else {
                0.0
            }

        val brightnessVariation =
            calculateBrightnessVariation(
                brightnessValues
            )

        val visualScore =
            calculateVisualScore(
                averageBrightness = averageBrightness,
                brightnessVariation = brightnessVariation,
                facePresence = facePresence,
                availableFrames = frames.size,
                expectedFrames = sampleCount
            )

        val hookScore =
            calculateHookScore(
                brightnessVariation = brightnessVariation,
                facePresence = facePresence,
                positionMs = startMs,
                videoDurationMs = videoDurationMs
            )

        val durationScore =
            calculateDurationScore(
                durationMs = windowDuration
            )

        val viralScore =
            calculateViralScore(
                visualScore = visualScore,
                hookScore = hookScore,
                durationScore = durationScore,
                facePresence = facePresence
            )

        val reasons =
            buildCandidateReasons(
                visualScore = visualScore,
                hookScore = hookScore,
                durationScore = durationScore,
                facePresence = facePresence,
                brightnessVariation = brightnessVariation
            )

        return ClipCandidate(
            startMs = startMs,
            endMs = endMs,
            visualScore = visualScore,
            hookScore = hookScore,
            durationScore = durationScore,
            viralScore = viralScore,
            facePresence = facePresence,
            reasons = reasons
        )

    } finally {
        frames.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}

fun calculateAverageBrightness(
    bitmap: Bitmap
): Double {
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return 0.0
    }

    val horizontalSamples = 12
    val verticalSamples = 12

    var totalBrightness = 0.0
    var sampleTotal = 0

    for (verticalIndex in 0 until verticalSamples) {
        val y =
            min(
                bitmap.height - 1,
                verticalIndex * bitmap.height /
                    verticalSamples
            )

        for (horizontalIndex in 0 until horizontalSamples) {
            val x =
                min(
                    bitmap.width - 1,
                    horizontalIndex * bitmap.width /
                        horizontalSamples
                )

            val pixel = bitmap.getPixel(x, y)

            val red =
                android.graphics.Color.red(pixel)

            val green =
                android.graphics.Color.green(pixel)

            val blue =
                android.graphics.Color.blue(pixel)

            val brightness =
                0.299 * red +
                    0.587 * green +
                    0.114 * blue

            totalBrightness += brightness
            sampleTotal += 1
        }
    }

    return if (sampleTotal > 0) {
        totalBrightness / sampleTotal
    } else {
        0.0
    }
}

fun calculateBrightnessVariation(
    values: List<Double>
): Double {
    if (values.size < 2) {
        return 0.0
    }

    var totalDifference = 0.0

    for (index in 1 until values.size) {
        totalDifference +=
            abs(values[index] - values[index - 1])
    }

    return totalDifference / (values.size - 1)
}

fun calculateVisualScore(
    averageBrightness: Double,
    brightnessVariation: Double,
    facePresence: Int,
    availableFrames: Int,
    expectedFrames: Int
): Int {
    val brightnessScore =
        when {
            averageBrightness in 70.0..190.0 -> 35
            averageBrightness in 45.0..220.0 -> 25
            averageBrightness > 0.0 -> 15
            else -> 0
        }

    val variationScore =
        when {
            brightnessVariation >= 35.0 -> 30
            brightnessVariation >= 20.0 -> 25
            brightnessVariation >= 10.0 -> 18
            brightnessVariation >= 4.0 -> 10
            else -> 5
        }

    val faceScore =
        (facePresence * 25 / 100)
            .coerceIn(0, 25)

    val frameScore =
        if (expectedFrames > 0) {
            availableFrames * 10 / expectedFrames
        } else {
            0
        }

    return (
        brightnessScore +
            variationScore +
            faceScore +
            frameScore
        ).coerceIn(0, 100)
}

fun calculateHookScore(
    brightnessVariation: Double,
    facePresence: Int,
    positionMs: Long,
    videoDurationMs: Long
): Int {
    val movementComponent =
        (brightnessVariation * 1.6)
            .toInt()
            .coerceIn(0, 45)

    val faceComponent =
        (facePresence * 35 / 100)
            .coerceIn(0, 35)

    val positionRatio =
        if (videoDurationMs > 0L) {
            positionMs.toDouble() /
                videoDurationMs.toDouble()
        } else {
            0.0
        }

    val positionComponent =
        when {
            positionRatio <= 0.15 -> 20
            positionRatio <= 0.50 -> 17
            positionRatio <= 0.85 -> 14
            else -> 10
        }

    return (
        movementComponent +
            faceComponent +
            positionComponent
        ).coerceIn(0, 100)
}

fun calculateDurationScore(
    durationMs: Long
): Int {
    val durationSeconds =
        durationMs / 1000L

    return when {
        durationSeconds in 25L..45L -> 100
        durationSeconds in 20L..55L -> 90
        durationSeconds in 15L..60L -> 80
        durationSeconds in 10L..70L -> 65
        else -> 45
    }
}

fun calculateViralScore(
    visualScore: Int,
    hookScore: Int,
    durationScore: Int,
    facePresence: Int
): Int {
    val weightedScore =
        visualScore * 0.35 +
            hookScore * 0.35 +
            durationScore * 0.20 +
            facePresence * 0.10

    return weightedScore
        .toInt()
        .coerceIn(0, 100)
}

fun buildCandidateReasons(
    visualScore: Int,
    hookScore: Int,
    durationScore: Int,
    facePresence: Int,
    brightnessVariation: Double
): List<String> {
    val reasons = mutableListOf<String>()

    if (visualScore >= 75) {
        reasons.add("Kualitas visual terdeteksi kuat.")
    } else if (visualScore >= 55) {
        reasons.add("Kualitas visual cukup baik.")
    }

    if (hookScore >= 75) {
        reasons.add(
            "Perubahan visual berpotensi menjadi pembuka yang menarik."
        )
    }

    if (durationScore >= 90) {
        reasons.add(
            "Durasi sesuai untuk TikTok, Reels, dan YouTube Shorts."
        )
    }

    if (facePresence >= 60) {
        reasons.add(
            "Wajah terlihat pada sebagian besar frame sampel."
        )
    } else if (facePresence > 0) {
        reasons.add(
            "Wajah terdeteksi pada beberapa frame."
        )
    }

    if (brightnessVariation >= 20.0) {
        reasons.add(
            "Terdapat perubahan visual yang cukup aktif."
        )
    }

    if (reasons.isEmpty()) {
        reasons.add(
            "Kandidat dipilih berdasarkan penyebaran posisi dalam video."
        )
    }

    return reasons
}
