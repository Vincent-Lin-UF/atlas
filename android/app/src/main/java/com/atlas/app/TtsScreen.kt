package com.atlas.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atlas.app.tts.TtsManager
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun TtsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tts = remember { TtsManager(context) }

    val text = remember {
        context.resources.openRawResource(R.raw.sample).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        append(line).append('\n')
                        line = reader.readLine()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Native TTS MVP (reads res/raw/sample.txt)")
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(text)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { tts.speak(text) },
                enabled = true
            ) {
                Text("Play")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = { tts.stop() }
            ) {
                Text("Stop")
            }
        }
    }
}
