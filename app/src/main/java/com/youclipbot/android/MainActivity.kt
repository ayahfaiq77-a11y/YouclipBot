package com.youclipbot.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    var exporting by remember {
        mutableStateOf(false)
    }

    var analysisStatus by remember {
        mutableStateOf("")
    }

    var exportStatus by remember {
        mutableStateOf("")
    }

    var exportedFiles by remember {
        mutableStateOf<List<String>>(
            emptyList()
        )
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
                        // Sebagian file picker tidak memberi izin permanen.
                    }

                    videoInfo =
                        getVideoInfo(
                            context = context,
                            uri = uri
                        )

                    clipCandidates = emptyList()
                    exportedFiles = emptyList()
                    analysisStatus = ""
                    exportStatus = ""
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
                text = "YouClipBot v2.3",
                style =
                    MaterialTheme.typography.headlineMedium
            )

            Text(
                text =
                    "Pilih video panjang, analisis maksimal " +
                        "5 kandidat berdasarkan kualitas visual, " +
                        "kemudian buat video vertikal 9:16."
            )

            Text(
                text =
                    "Estimated Viral Score merupakan estimasi " +
                        "visual dan bukan jaminan video akan viral."
            )

            Button(
                modifier = Modifier.fillMaxWidth(),

                enabled =
                    !analyzing &&
                        !exporting,

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

                    enabled =
                        !analyzing &&
                            !exporting,

                    onClick = {

                        scope.launch {

                            analyzing = true
                            errorMessage = null
                            clipCandidates = emptyList()
                            exportedFiles = emptyList()
                            exportStatus = ""

                            analysisStatus =
                                "Menyiapkan analisis video..."

                            try {

                                val results =
                                    analyzeVideo(
                                        context = context,
                                        video = video,

                                        onProgress = { message ->
                                            analysisStatus =
                                                message
                                        }
                                    )

                                clipCandidates =
                                    results

                                analysisStatus =
                                    if (results.isNotEmpty()) {

                                        "Analisis selesai. " +
                                            "${results.size} kandidat ditemukan."

                                    } else {

                                        "Analisis selesai, tetapi " +
                                            "tidak ditemukan kandidat."
                                    }

                            } catch (exception: Exception) {

                                errorMessage =
                    
