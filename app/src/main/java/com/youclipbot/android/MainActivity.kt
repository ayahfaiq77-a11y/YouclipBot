package com.youclipbot.android

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

@Composable
fun YouClipBotApp(context: Context) {

    var videoInfo by remember {
        mutableStateOf<VideoInfo?>(null)
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

                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    videoInfo = getVideoInfo(context, uri)
                    errorMessage = null

                } catch (e: Exception) {
                    errorMessage =
                        "Gagal membaca video: ${e.message}"
                }
            }
        }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "YouClipBot v2",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                "Pilih video panjang yang akan dibuat menjadi maksimal 5 clip."
            )

            Button(
                modifier = Modifier.fillMaxWidth(),

                onClick = {
                    videoPicker.launch(
                        arrayOf("video/*")
                    )
                }
            ) {

                Text("Pilih Video")
            }

            videoInfo?.let { video ->

                HorizontalDivider()

                Text(
                    "Video terpilih",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    "Nama: ${video.name}"
                )

                Text(
                    "Ukuran: %.1f MB".format(
                        video.sizeMb
                    )
                )

                Text(
                    "Durasi: ${
                        formatDuration(
                            video.durationMs
                        )
                    }"
                )

                Button(
                    modifier =
                        Modifier.fillMaxWidth(),

                    onClick = {
                        // tahap berikutnya:
                        // analisis video
                    }
                ) {

                    Text(
                        "Analisis Video"
                    )
                }
            }

            errorMessage?.let {

                Text(
                    text = it,
                    color =
                        MaterialTheme.colorScheme.error
                )
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

            if (nameIndex >= 0)
                name = it.getString(nameIndex)

            if (sizeIndex >= 0)
                size = it.getLong(sizeIndex)
        }
    }

    val retriever =
        MediaMetadataRetriever()

    retriever.setDataSource(
        context,
        uri
    )

    val duration =
        retriever.extractMetadata(
    MediaMetadataRetriever.METADATA_KEY_DURATION
)

    retriever.release()

    return VideoInfo(
        uri = uri,
        name = name,
        sizeMb =
            size / 1024.0 / 1024.0,
        durationMs = duration
    )
}

fun formatDuration(
    durationMs: Long
): String {

    val totalSeconds =
        durationMs / 1000

    val hours =
        totalSeconds / 3600

    val minutes =
        (totalSeconds % 3600) / 60

    val seconds =
        totalSeconds % 60

    return if (hours > 0) {

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
