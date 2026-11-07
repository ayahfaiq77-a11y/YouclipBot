package com.youclipbot.android

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                YouClipBotApp(this)
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
        mutableStateOf<List<ClipCandidate>>(
            emptyList()
        )
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
            contract =
                ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    try {

                        context.contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )

                    } catch (_: SecurityException) {

                        /*
                         * Beberapa file picker tidak memberi
                         * persistable permission.
                         */
                    }

                    videoInfo =
                        getVideoInfo(
                            context = context,
                            uri = uri
                        )

                    clipCandidates =
                        emptyList()

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

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text =
                    "YouClipBot v2.2",

                style =
                    MaterialTheme.typography
                        .headlineMedium
            )

            Text(
                text =
                    "Pilih video panjang. YouClipBot akan " +
                    "menganalisis wajah, komposisi visual, " +
                    "perubahan visual pembuka, dan durasi " +
                    "untuk mencari maksimal 5 kandidat klip."
            )

            Text(
                text =
                    "Estimated Viral Score adalah estimasi " +
                    "visual, bukan jaminan video akan viral."
            )

            Button(
                modifier =
                    Modifier.fillMaxWidth(),

                enabled = !analyzing,

                onClick = {

                    videoPicker.launch(
                        arrayOf("video/*")
                    )
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

                    style =
                        MaterialTheme.typography
                            .titleMedium
                )

                Text(
                    text =
                        "Nama: ${video.name}"
                )

                Text(
                    text =
                        "Ukuran: %.1f MB".format(
                            video.sizeMb
                        )
                )

                Text(
                    text =
                        "Durasi: ${
                            formatDuration(
                                video.durationMs
                            )
                        }"
                )

                Button(
                    modifier =
                        Modifier.fillMaxWidth(),

                    enabled = !analyzing,

                    onClick = {

                        scope.launch {

                            analyzing = true

                            clipCandidates =
                                emptyList()

                            errorMessage = null

                            analysisStatus =
                                "Menyiapkan analisis video..."

                            try {

                                val results =
                                    analyzeVideo(
                                        context = context,
                                        video = video,

                                        onProgress = {
                                            message ->

                                            analysisStatus =
                                                message
                                        }
                                    )

                                clipCandidates =
                                    results

                                analysisStatus =
                                    if (
                                        results.isNotEmpty()
                                    ) {

                                        "Analisis selesai. " +
                                        "${results.size} kandidat ditemukan."

                                    } else {

                                        "Analisis selesai, tetapi " +
                                        "tidak ditemukan kandidat."
                                    }

                            } catch (
                                exception: Exception
                            ) {

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
                                "Analisis Potensi Viral"
                            }
                    )
                }
            }

            if (analyzing) {

                LinearProgressIndicator(
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            if (
                analysisStatus.isNotBlank()
            ) {

                Text(
                    text = analysisStatus
                )
            }

            errorMessage?.let { message ->

                Text(
                    text = message,

                    color =
                        MaterialTheme.colorScheme
                            .error
                )
            }

            if (
                clipCandidates.isNotEmpty()
            ) {

                HorizontalDivider()

                Text(
                    text =
                        "Top ${clipCandidates.size} Kandidat",

                    style =
                        MaterialTheme.typography
                            .titleMedium
                )

                clipCandidates.forEachIndexed {
                    index,
                    clip ->

                    ClipCandidateCard(
                        position = index + 1,
                        clip = clip
                    )
                }
            }
        }
    }
}

@Composable
fun ClipCandidateCard(
    position: Int,
    clip: ClipCandidate
) {

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(7.dp)
        ) {

            Text(
                text =
                    "#$position • Estimated Viral Score " +
                    "${clip.viralScore}/100",

                style =
                    MaterialTheme.typography
                        .titleMedium
            )

            Text(
                text =
                    viralLabel(
                        clip.viralScore
                    )
            )

            Text(
                text =
                    "${formatDuration(clip.startMs)}" +
                    " - " +
                    formatDuration(clip.endMs)
            )

            Text(
                text =
                    "Durasi klip: " +
                    formatDuration(
                        clip.endMs -
                        clip.startMs
                    )
            )

            HorizontalDivider()

            Text(
                text =
                    "Visual Score: " +
                    "${clip.visualScore}/100"
            )

            Text(
                text =
                    "Opening Hook Visual: " +
                    "${clip.hookScore}/100"
            )

            Text(
                text =
                    "Duration Score: " +
                    "${clip.durationScore}/100"
            )

            Text(
                text =
                    "Wajah terdeteksi: " +
                    "${clip.facePresence}%"
            )

            if (
                clip.reasons.isNotEmpty()
            ) {

                HorizontalDivider()

                Text(
                    text = "Alasan penilaian:"
                )

                clip.reasons.forEach {
                    reason ->

                    Text(
                        text = "• $reason"
                    )
                }
            }
        }
    }
}

fun getVideoInfo(
    context: Context,
    uri: Uri
): VideoInfo {

    var name = "video"
    var size = 0L

    val cursor: Cursor? =
        context.contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

    cursor?.use {

        val nameIndex =
            it.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

        val sizeIndex =
            it.getColumnIndex(
                OpenableColumns.SIZE
            )

        if (it.moveToFirst()) {

            if (nameIndex >= 0) {

                name =
                    it.getString(nameIndex)
                        ?: "video"
            }

            if (sizeIndex >= 0) {

                size =
                    it.getLong(sizeIndex)
            }
        }
    }

    val retriever =
        MediaMetadataRetriever()

    return try {

        retriever.setDataSource(
            context,
            uri
        )

        val duration =
            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

        VideoInfo(
            uri = uri,
            name = name,

            sizeMb =
                size.toDouble() /
                1024.0 /
                1024.0,

            durationMs = duration
        )

    } finally {

        retriever.release()
    }
}

suspend fun analyzeVideo(
    context: Context,
    video: VideoInfo,
    onProgress: (String) -> Unit
): List<ClipCandidate> =
    withContext(Dispatchers.IO) {

        if (video.durationMs <= 0L) {

            throw IllegalArgumentException(
                "Durasi video tidak dapat dibaca."
            )
        }

        val retriever =
            MediaMetadataRetriever()

        val detector =
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(
                        FaceDetectorOptions
                            .PERFORMANCE_MODE_FAST
                    )
                    .enableTracking()
                    .build()
            )

        try {

            retriever.setDataSource(
                context,
                video.uri
            )

            val normalClipLength =
                30_000L

            val minimumClipLength =
                10_000L

            val clipLength =
                minOf(
                    normalClipLength,
                    video.durationMs
                )

            if (
                clipLength <
                minimumClipLength
            ) {

                throw IllegalArgumentException(
                    "Video terlalu pendek. " +
                    "Gunakan video minimal 10 detik."
                )
            }

            /*
             * Maksimal sekitar 120 kandidat.
             * Ini mengurangi beban pada POCO X3.
             */
            val candidateStep =
                max(
                    15_000L,
                    video.durationMs / 120L
                )

            val candidates =
                mutableListOf<ClipCandidate>()

            var startMs = 0L
            var candidateNumber = 0

            while (
                startMs <
                video.durationMs
            ) {

                val endMs =
                    minOf(
                        startMs + clipLength,
                        video.durationMs
                    )

                if (
                    endMs - startMs <
                    minimumClipLength
                ) {
                    break
                }

                candidateNumber++

                withContext(
                    Dispatchers.Main
                ) {

                    onProgress(
                        "Menganalisis kandidat " +
                        "$candidateNumber..."
                    )
                }

                val result =
                    analyzeClipCandidate(
                        retriever = retriever,
                        detector = detector,
                        startMs = startMs,
                        endMs = endMs
                    )

                if (result != null) {

                    candidates.add(result)
                }

                startMs +=
                    candidateStep
            }

            selectTopClips(
                candidates = candidates,
                maximumClips = 5
            )

        } finally {

            detector.close()
            retriever.release()
        }
    }

suspend fun analyzeClipCandidate(
    retriever: MediaMetadataRetriever,

    detector:
        com.google.mlkit.vision.face.FaceDetector,

    startMs: Long,
    endMs: Long
): ClipCandidate? {

    var validSamples = 0
    var faceSamples = 0

    var faceAreaTotal = 0.0
    var centerScoreTotal = 0.0

    var firstBrightness: Double? = null
    var secondBrightness: Double? = null

    val sampleCount = 6

    for (
        sampleIndex
        in 0 until sampleCount
    ) {

        val samplePositionMs =
            startMs +
            (
                (endMs - startMs) *
                (sampleIndex + 1) /
                (sampleCount + 1)
            )

        val bitmap: Bitmap? =
            retriever.getFrameAtTime(
                samplePositionMs * 1000L,

                MediaMetadataRetriever
                    .OPTION_CLOSEST_SYNC
            )

        if (bitmap != null) {

            validSamples++

            try {

                if (sampleIndex == 0) {

                    firstBrightness =
                        calculateFrameBrightness(
                            bitmap
                        )

                } else if (
                    sampleIndex == 1
                ) {

                    secondBrightness =
                        calculateFrameBrightness(
                            bitmap
                        )
                }

                val inputImage =
                    InputImage.fromBitmap(
                        bitmap,
                        0
                    )

                val faces =
                    detector
                        .process(inputImage)
                        .await()

                val primaryFace =
                    faces.maxByOrNull {
                        face ->

                        face.boundingBox
                            .width() *

                        face.boundingBox
                            .height()
                    }

                if (primaryFace != null) {

                    faceSamples++

                    val frameArea =
                        bitmap.width.toDouble() *
                        bitmap.height.toDouble()

                    val faceArea =
                        primaryFace.boundingBox
                            .width()
                            .toDouble() *

                        primaryFace.boundingBox
                            .height()
                            .toDouble()

                    if (frameArea > 0.0) {

                        faceAreaTotal +=
                            faceArea /
                            frameArea
                    }

                    val faceCenterX =
                        primaryFace.boundingBox
                            .centerX()
                            .toDouble()

                    val frameCenterX =
                        bitmap.width /
                        2.0

                    val distanceFromCenter =
                        abs(
                            faceCenterX -
                            frameCenterX
                        )

                    val maximumDistance =
                        frameCenterX
                            .coerceAtLeast(1.0)

                    val centerScore =
                        (
                            1.0 -
                            (
                                distanceFromCenter /
                                maximumDistance
                            )
                        ).coerceIn(
                            0.0,
                            1.0
                        )

                    centerScoreTotal +=
                        centerScore
                }

            } finally {

                bitmap.recycle()
            }
        }
    }

    if (validSamples <= 0) {

        return null
    }

    val facePresence =
        faceSamples.toDouble() /
        validSamples.toDouble()

    val averageFaceArea =
        if (faceSamples > 0) {

            faceAreaTotal /
            faceSamples.toDouble()

        } else {

            0.0
        }

    val averageCenterScore =
        if (faceSamples > 0) {

            centerScoreTotal /
            faceSamples.toDouble()

        } else {

            0.0
        }

    val openingVisualChange =
        if (
            firstBrightness != null &&
            secondBrightness != null
        ) {

            (
                abs(
                    firstBrightness -
                    secondBrightness
                ) / 0.35
            ).coerceIn(
                0.0,
                1.0
            )

        } else {

            0.0
        }

    val durationMs =
        endMs - startMs

    val visualScore =
        calculateVisualScore(
            facePresence =
                facePresence,

            averageFaceArea =
                averageFaceArea,

            averageCenterScore =
                averageCenterScore
        )

    val hookScore =
        (
            openingVisualChange *
            100.0
        )
            .toInt()
            .coerceIn(
                0,
                100
            )

    val durationScore =
        calculateDurationScore(
            durationMs
        )

    val viralResult =
        calculateEstimatedViralScore(
            facePresence =
                facePresence,

            averageFaceArea =
                averageFaceArea,

            averageCenterScore =
                averageCenterScore,

            openingVisualChange =
                openingVisualChange,

            clipDurationMs =
                durationMs
        )

    return ClipCandidate(
        startMs = startMs,
        endMs = endMs,

        visualScore =
            visualScore,

        hookScore =
            hookScore,

        durationScore =
            durationScore,

        viralScore =
            viralResult.first,

        facePresence =
            (
                facePresence *
                100.0
            )
                .toInt()
                .coerceIn(
                    0,
                    100
                ),

        reasons =
            viralResult.second
    )
}

fun calculateVisualScore(
    facePresence: Double,
    averageFaceArea: Double,
    averageCenterScore: Double
): Int {

    val presenceScore =
        facePresence
            .coerceIn(
                0.0,
                1.0
            ) *
        50.0

    val faceSizeScore =
        (
            averageFaceArea
                .coerceAtMost(0.25) /
            0.25
        ) *
        30.0

    val centerScore =
        averageCenterScore
            .coerceIn(
                0.0,
                1.0
            ) *
        20.0

    return (
        presenceScore +
        faceSizeScore +
        centerScore
    )
        .toInt()
        .coerceIn(
            0,
            100
        )
}

fun calculateDurationScore(
    durationMs: Long
): Int {

    val durationSeconds =
        durationMs /
        1000L

    return when (
        durationSeconds
    ) {

        in 20L..35L ->
            100

        in 15L..45L ->
            75

        in 10L..60L ->
            50

        else ->
            25
    }
}

fun calculateEstimatedViralScore(
    facePresence: Double,
    averageFaceArea: Double,
    averageCenterScore: Double,
    openingVisualChange: Double,
    clipDurationMs: Long
): Pair<Int, List<String>> {

    val reasons =
        mutableListOf<String>()

    /*
     * Bobot Estimated Viral Score:
     *
     * 30% konsistensi wajah
     * 15% ukuran wajah
     * 15% posisi wajah
     * 25% perubahan visual pembuka
     * 15% durasi
     */

    val facePresenceScore =
        facePresence
            .coerceIn(
                0.0,
                1.0
            ) *
        30.0

    if (facePresence >= 0.80) {

        reasons.add(
            "Wajah terlihat konsisten"
        )
    }

    val faceSizeScore =
        (
            averageFaceArea
                .coerceAtMost(0.25) /
            0.25
        ) *
        15.0

    if (averageFaceArea >= 0.08) {

        reasons.add(
            "Ukuran wajah cukup jelas"
        )
    }

    val centerPositionScore =
        averageCenterScore
            .coerceIn(
                0.0,
                1.0
            ) *
        15.0

    if (
        averageCenterScore >= 0.75
    ) {

        reasons.add(
            "Subjek dekat area tengah"
        )
    }

    val openingHookScore =
        openingVisualChange
            .coerceIn(
                0.0,
                1.0
            ) *
        25.0

    if (
        openingVisualChange >= 0.35
    ) {

        reasons.add(
            "Pembukaan memiliki perubahan visual"
        )
    }

    val durationScore =
        calculateDurationScore(
            clipDurationMs
        ) / 100.0 * 15.0

    val durationSeconds =
        clipDurationMs /
        1000L

    if (
        durationSeconds in 20L..35L
    ) {

        reasons.add(
            "Durasi cocok untuk video pendek"
        )
    }

    if (
        facePresence < 0.40
    ) {

        reasons.add(
            "Wajah kurang konsisten"
        )
    }

    if (
        openingVisualChange < 0.10
    ) {

        reasons.add(
            "Pembukaan cenderung statis"
        )
    }

    val total =
        (
            facePresenceScore +
            faceSizeScore +
            centerPositionScore +
            openingHookScore +
            durationScore
        )
            .toInt()
            .coerceIn(
                0,
                100
            )

    return Pair(
        total,
        reasons
    )
}

fun calculateFrameBrightness(
    bitmap: Bitmap
): Double {

    val sampleWidth =
        minOf(
            bitmap.width,
            48
        )

    val sampleHeight =
        minOf(
            bitmap.height,
            48
        )

    if (
        sampleWidth <= 0 ||
        sampleHeight <= 0
    ) {

        return 0.0
    }

    val scaledBitmap =
        Bitmap.createScaledBitmap(
            bitmap,
            sampleWidth,
            sampleHeight,
            false
        )

    var brightnessTotal = 0.0
    var pixelCount = 0

    try {

        for (
            x in 0 until
            scaledBitmap.width
        ) {

            for (
                y in 0 until
                scaledBitmap.height
            ) {

                val pixel =
                    scaledBitmap.getPixel(
                        x,
                        y
                    )

                val brightness =
                    (
                        Color.red(pixel) *
                        0.299 +

                        Color.green(pixel) *
                        0.587 +

                        Color.blue(pixel) *
                        0.114
                    ) / 255.0

                brightnessTotal +=
                    brightness

                pixelCount++
            }
        }

    } finally {

        if (
            scaledBitmap !== bitmap
        ) {

            scaledBitmap.recycle()
        }
    }

    return if (pixelCount > 0) {

        brightnessTotal /
        pixelCount.toDouble()

    } else {

        0.0
    }
}

fun selectTopClips(
    candidates: List<ClipCandidate>,
    maximumClips: Int
): List<ClipCandidate> {

    val selected =
        mutableListOf<ClipCandidate>()

    val sortedCandidates =
        candidates.sortedWith(
            compareByDescending<ClipCandidate> {
                it.viralScore
            }.thenByDescending {
                it.visualScore
            }
        )

    for (
        candidate
        in sortedCandidates
    ) {

        val overlaps =
            selected.any { existing ->

                candidate.startMs <
                    existing.endMs &&

                candidate.endMs >
                    existing.startMs
            }

        if (!overlaps) {

            selected.add(candidate)

            if (
                selected.size >=
                maximumClips
            ) {

                break
            }
        }
    }

    return selected
}

fun viralLabel(
    score: Int
): String {

    return when {

        score >= 85 ->
            "Potensi visual sangat tinggi"

        score >= 70 ->
            "Potensi visual tinggi"

        score >= 55 ->
            "Potensi visual sedang"

        score >= 40 ->
            "Potensi visual rendah"

        else ->
            "Perlu perbaikan visual"
    }
}

fun formatDuration(
    durationMs: Long
): String {

    val safeDuration =
        durationMs.coerceAtLeast(0L)

    val totalSeconds =
        safeDuration /
        1000L

    val hours =
        totalSeconds /
        3600L

    val minutes =
        (
            totalSeconds %
            3600L
        ) / 60L

    val seconds =
        totalSeconds %
        60L

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
