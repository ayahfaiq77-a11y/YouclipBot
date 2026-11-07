package com.youclipbot.android

import android.content.Context
import android.content.Intent
import android.database.Cursor
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
    val score: Int,
    val facePresence: Int
)

@Composable
fun YouClipBotApp(context: Context) {

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
            contract = ActivityResultContracts.OpenDocument()
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
                        // Beberapa file picker tidak memberikan
                        // persistable permission.
                        // File masih dapat digunakan selama izin aktif.
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
                text = "YouClipBot v2.1",
                style =
                    MaterialTheme.typography.headlineMedium
            )

            Text(
                text =
                    "Pilih video panjang, lalu YouClipBot " +
                    "akan mencari maksimal 5 kandidat klip " +
                    "berdasarkan keberadaan dan ukuran wajah."
            )

            Button(
                modifier = Modifier.fillMaxWidth(),

                enabled = !analyzing,

                onClick = {

                    videoPicker.launch(
                        arrayOf("video/*")
                    )
                }
            ) {

                Text(
                    if (videoInfo == null)
                        "Pilih Video"
                    else
                        "Ganti Video"
                )
            }

            videoInfo?.let { video ->

                HorizontalDivider()

                Text(
                    text = "Video terpilih",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Nama: ${video.name}"
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
                    modifier = Modifier.fillMaxWidth(),

                    enabled = !analyzing,

                    onClick = {

                        scope.launch {

                            analyzing = true
                            errorMessage = null
                            clipCandidates =
                                emptyList()

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
                                        "tidak ditemukan kandidat yang sesuai."
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
                        if (analyzing)
                            "Sedang Menganalisis..."
                        else
                            "Analisis Video"
                    )
                }
            }

            if (analyzing) {

                LinearProgressIndicator(
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            if (analysisStatus.isNotBlank()) {

                Text(
                    text = analysisStatus
                )
            }

            errorMessage?.let { message ->

                Text(
                    text = message,
                    color =
                        MaterialTheme.colorScheme.error
                )
            }

            if (clipCandidates.isNotEmpty()) {

                HorizontalDivider()

                Text(
                    text =
                        "Top ${clipCandidates.size} Clips",

                    style =
                        MaterialTheme.typography.titleMedium
                )

                clipCandidates.forEachIndexed {
                    index,
                    clip ->

                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(16.dp),

                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {

                            Text(
                                text =
                                    "#${index + 1} • " +
                                    "Score ${clip.score}",

                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
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

                            Text(
                                text =
                                    "Wajah terdeteksi: " +
                                    "${clip.facePresence}%"
                            )
                        }
                    }
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

        val detectorOptions =
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions
                        .PERFORMANCE_MODE_FAST
                )
                .enableTracking()
                .build()

        val detector =
            FaceDetection.getClient(
                detectorOptions
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
             * Untuk video panjang, step dibuat lebih besar
             * agar jumlah kandidat tidak berlebihan.
             *
             * Maksimal kurang lebih 120 kandidat.
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

                var validSamples = 0
                var faceSamples = 0
                var faceAreaTotal = 0.0
                var centerScoreTotal = 0.0

                val sampleCount = 5

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
                                    bitmap.width
                                        .toDouble() *
                                    bitmap.height
                                        .toDouble()

                                val faceArea =
                                    primaryFace
                                        .boundingBox
                                        .width()
                                        .toDouble() *
                                    primaryFace
                                        .boundingBox
                                        .height()
                                        .toDouble()

                                if (frameArea > 0.0) {

                                    faceAreaTotal +=
                                        faceArea /
                                        frameArea
                                }

                                val faceCenterX =
                                    primaryFace
                                        .boundingBox
                                        .centerX()
                                        .toDouble()

                                val frameCenterX =
                                    bitmap.width /
                                    2.0

                                val distanceFromCenter =
                                    kotlin.math.abs(
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

                if (validSamples > 0) {

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

                    /*
                     * Skor v2.1:
                     * 60% konsistensi wajah
                     * 25% ukuran wajah
                     * 15% posisi wajah dekat tengah
                     */
                    val score =
                        (
                            facePresence * 60.0 +

                            (
                                averageFaceArea
                                    .coerceAtMost(
                                        0.25
                                    ) /
                                0.25
                            ) * 25.0 +

                            averageCenterScore *
                            15.0
                        )
                            .toInt()
                            .coerceIn(
                                0,
                                100
                            )

                    candidates.add(
                        ClipCandidate(
                            startMs = startMs,
                            endMs = endMs,
                            score = score,
                            facePresence =
                                (
                                    facePresence *
                                    100.0
                                )
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        100
                                    )
                        )
                    )
                }

                startMs += candidateStep
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

fun selectTopClips(
    candidates: List<ClipCandidate>,
    maximumClips: Int
): List<ClipCandidate> {

    val selected =
        mutableListOf<ClipCandidate>()

    val sortedCandidates =
        candidates.sortedByDescending {
            it.score
        }

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

fun formatDuration(
    durationMs: Long
): String {

    val safeDuration =
        durationMs.coerceAtLeast(0L)

    val totalSeconds =
        safeDuration / 1000L

    val hours =
        totalSeconds / 3600L

    val minutes =
        (
            totalSeconds %
            3600L
        ) / 60L

    val seconds =
        totalSeconds % 60L

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
