package com.example.aidilkocu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val Green = Color(0xFF16834A)
private val Red = Color(0xFFC62828)
private val Ink = Color(0xFF172033)
// Replace with your deployed HTTPS backend URL (no trailing slash).
private const val API_BASE_URL = "https://YOUR_BACKEND_HOST"
private data class ChatLine(val speaker: String, val text: String, val corrected: String? = null, val isUser: Boolean = false)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var onSpeechResult: ((String) -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setContent { CoachScreen(::startListening, ::speak) }
    }
    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS }
    private fun speak(text: String, language: String) {
        if (!ttsReady) return
        val locale = languageLocale(language)
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach_reply")
    }
    private fun startListening(language: String, callback: (String) -> Unit) {
        onSpeechResult = callback
        val recognizer = speechRecognizer ?: return
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onSpeechResult?.invoke("") }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onSpeechResult?.invoke(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale(language).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        recognizer.startListening(intent)
    }
    override fun onDestroy() { speechRecognizer?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

private fun languageLocale(language: String): Locale = when (language) {
    "English" -> Locale.forLanguageTag("en-US")
    "German" -> Locale.GERMANY
    "French" -> Locale.FRANCE
    "Spanish" -> Locale("es", "ES")
    "Italian" -> Locale.ITALY
    "Japanese" -> Locale.JAPAN
    "Portuguese" -> Locale("pt", "BR")
    "Turkish" -> Locale("tr", "TR")
    else -> Locale.UK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachScreen(listen: (String, (String) -> Unit) -> Unit, speak: (String, String) -> Unit) {
    var language by remember { mutableStateOf("English") }
    var level by remember { mutableStateOf("A2 · Elementary") }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Configure your backend URL to start AI practice") }
    var lines by remember { mutableStateOf(listOf(ChatLine("Kişisel Dil Koçum", "Merhaba! Başlamak için hedef dili ve seviyeni seç, ardından mikrofon düğmesine bas. Gerçek AI yanıtları backend URL’si ayarlandığında etkinleşir."))) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { listening = true; status = "Listening…"; listen(language) { result ->
            listening = false
            if (result.isBlank()) { status = "Couldn't hear you. Try again." } else {
                lines = lines + ChatLine("You", result, result, true)
                status = "AI is thinking…"
                requestCoachReply(language, level, result, lines.takeLast(8).map { "${it.speaker}: ${it.text}" }) { response, error ->
                    if (response != null) {
                        val lastUserIndex = lines.indexOfLast { it.isUser && it.text == result }
                        if (lastUserIndex >= 0) lines = lines.toMutableList().also { it[lastUserIndex] = it[lastUserIndex].copy(corrected = response.optString("corrected", result)) }
                        val explanation = response.optString("explanation", "")
                        val reply = response.optString("reply", "")
                        lines = lines + ChatLine("Correction", explanation.ifBlank { "Review the suggested correction above." }) + ChatLine("AI Coach", reply)
                        status = "Connected · AI language coach"
                        speak(reply, language)
                    } else status = error ?: "AI service error" 
                }
            }
        } } else status = "Microphone permission is required" 
    }
    Scaffold(containerColor = Color(0xFFF5F7FB), topBar = {
        TopAppBar(title = { Column { Text("Kişisel Dil Koçum", fontWeight = FontWeight.Bold); Text("Speak · Learn · Improve", fontSize = 12.sp, color = Color.Gray) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOUR PRACTICE", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        var langExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(value = language, onValueChange = {}, readOnly = true, label = { Text("Language") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                                listOf("English", "German", "French", "Spanish", "Italian", "Japanese", "Portuguese", "Turkish").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { language = it; langExpanded = false }) }
                            }
                        }
                        var levelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = levelExpanded, onExpandedChange = { levelExpanded = it }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(value = level, onValueChange = {}, readOnly = true, label = { Text("Level") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(levelExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = levelExpanded, onDismissRequest = { levelExpanded = false }) {
                                listOf("A1 · Beginner", "A2 · Elementary", "B1 · Intermediate", "B2 · Upper intermediate", "C1 · Advanced", "C2 · Proficient").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { level = it; levelExpanded = false }) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(if (listening) Red else Green, RoundedCornerShape(50)))
                        Text(status, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            Text("Conversation", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(lines) { line ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (line.isUser) Color(0xFFEAF2FF) else Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(line.speaker, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (line.isUser) Color(0xFF2457A7) else Green)
                            if (line.isUser && line.corrected != null) {
                                Text(buildAnnotatedString {
                                    withStyle(SpanStyle(color = Red, fontWeight = FontWeight.Medium)) { append(line.text) }
                                    if (line.corrected != line.text) {
                                        append("\n")
                                        withStyle(SpanStyle(color = Green, fontWeight = FontWeight.Medium)) { append(line.corrected) }
                                    } else {
                                        append("\n")
                                        withStyle(SpanStyle(color = Green, fontWeight = FontWeight.Medium)) { append("✓ Looks good!") }
                                    }
                                }, fontSize = 15.sp, lineHeight = 23.sp)
                                Text("Red = original speech · Green = suggested correction", fontSize = 11.sp, color = Color.Gray)
                            } else Text(line.text, fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
                            if (!line.isUser) TextButton(onClick = { speak(line.text, language) }) { Text("▶ Listen again") }
                        }
                    }
                }
            }
            Button(onClick = {
                if (listening) { listening = false; status = "Ready to practice" }
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    listening = true; status = "Listening…"
                    listen(language) { result ->
                        listening = false
                        if (result.isBlank()) status = "Couldn't hear you. Try again." else {
                            lines = lines + ChatLine("You", result, result, true)
                            status = "AI is thinking…"
                            requestCoachReply(language, level, result, lines.takeLast(8).map { "${it.speaker}: ${it.text}" }) { response, error ->
                                if (response != null) {
                                    val lastUserIndex = lines.indexOfLast { it.isUser && it.text == result }
                                    if (lastUserIndex >= 0) lines = lines.toMutableList().also { it[lastUserIndex] = it[lastUserIndex].copy(corrected = response.optString("corrected", result)) }
                                    val explanation = response.optString("explanation", "")
                                    val reply = response.optString("reply", "")
                                    lines = lines + ChatLine("Correction", explanation.ifBlank { "Review the suggested correction above." }) + ChatLine("AI Coach", reply)
                                    status = "Connected · AI language coach"
                                    speak(reply, language)
                                } else status = error ?: "AI service error"
                            }
                        }
                    }
                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF315FEA))) {
                Text(if (listening) "●  Listening…" else "🎙  Speak to your AI coach", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text("Speech recognition and voice playback use Android services; AI feedback requires your backend URL.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}


private fun requestCoachReply(
    language: String,
    level: String,
    userText: String,
    history: List<String>,
    callback: (JSONObject?, String?) -> Unit
) {
    Thread {
        var connection: HttpURLConnection? = null
        try {
            require(!API_BASE_URL.contains("YOUR_BACKEND_HOST")) { "Set API_BASE_URL in MainActivity.kt to your deployed backend URL." }
            connection = (URL("$API_BASE_URL/api/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val body = JSONObject().apply {
                put("language", language)
                put("level", level.substringBefore(" ·"))
                put("userText", userText)
                put("history", org.json.JSONArray(history))
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("AI server returned HTTP $code: ${responseText.take(180)}")
            val json = JSONObject(responseText)
            Handler(Looper.getMainLooper()).post { callback(json, null) }
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("YOUR_BACKEND_HOST") == true -> "Set the backend URL in MainActivity.kt. See backend/README.md."
                else -> "AI connection failed: ${e.message ?: "unknown error"}"
            }
            Handler(Looper.getMainLooper()).post { callback(null, message) }
        } finally {
            connection?.disconnect()
        }
    }.start()
}
