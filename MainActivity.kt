package com.youclipbot.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) { Text("YouClipBot", style=MaterialTheme.typography.headlineMedium); Text("Android v1 cloud build berhasil dipasang."); Button(onClick={}) { Text("Pilih Video") } } } } } }
}
