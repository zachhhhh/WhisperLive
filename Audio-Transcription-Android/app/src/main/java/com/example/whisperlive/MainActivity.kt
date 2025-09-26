package com.example.whisperlive

import android.Manifest
import android.util.Log
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private lateinit var spinnerLanguage: Spinner
    private lateinit var tvOutput: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var webSocketClient: WebSocketClient? = null
    private var isWebSocketOpen = false
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var translationEnabled = false
    private var lastSourceText: String = ""
    private var lastTranslatedText: String = ""

    private lateinit var languageCodes: Array<String>
    private lateinit var languageNames: Array<String>

    private val models = arrayOf("tiny", "base", "small", "medium", "large")

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = max(AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat), sampleRate)

    private val permissionRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        languageCodes = resources.getStringArray(R.array.translation_language_codes)
        languageNames = resources.getStringArray(R.array.translation_language_names)

        btnRecord = findViewById(R.id.btnRecord)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        tvOutput = findViewById(R.id.tvTranscription)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        val savedLanguageIndex = sharedPreferences.getInt("language_index", 0)
            .coerceIn(0, languageCodes.size - 1)
        spinnerLanguage.setSelection(savedLanguageIndex)

        tvOutput.text = getString(R.string.output_placeholder)

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                if (checkPermissions()) {
                    startRecording()
                } else {
                    requestPermissions()
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                showToast(getString(R.string.toast_permission_required))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        Log.i("MainActivity", "Starting recording")

        val host = sharedPreferences.getString("host", getString(R.string.default_server_host)).orEmpty()
        val portValue = sharedPreferences.getString("port", getString(R.string.default_server_port)).orEmpty()

        Log.d("MainActivity", "Connecting to server: $host:$portValue")

        if (host.isBlank() || portValue.isBlank()) {
            Log.e("MainActivity", "Missing server host or port")
            showToast(getString(R.string.toast_missing_server))
            return
        }

        val port = portValue.toIntOrNull()
        if (port == null) {
            Log.e("MainActivity", "Invalid port: $portValue")
            showToast(getString(R.string.toast_invalid_port))
            return
        }

        translationEnabled = true
        lastSourceText = ""
        lastTranslatedText = ""

        sharedPreferences.edit()
            .putInt("language_index", spinnerLanguage.selectedItemPosition)
            .apply()

        val targetLanguage = languageCodes[spinnerLanguage.selectedItemPosition]
        val modelIndex = sharedPreferences.getInt("model_index", 2)
            .coerceIn(0, models.size - 1)
        val model = models[modelIndex]

        tvOutput.text = getString(R.string.status_connecting)
        btnRecord.text = getString(R.string.start_button_stop)

        val uid = UUID.randomUUID().toString()

        val json = JSONObject().apply {
            put("uid", uid)
            put("language", "en")
            put("task", "transcribe")
            put("model", model)
            put("use_vad", true)
            put("enable_translation", true)
            put("target_language", targetLanguage)
            put("send_last_n_segments", 10)
        }

        val uri = URI("ws://$host:$port")
        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshake: ServerHandshake) {
                Log.i("MainActivity", "WebSocket connected: $handshake")
                isWebSocketOpen = true
                runOnUiThread {
                    tvOutput.text = getString(R.string.status_listening)
                }
                send(json.toString())
            }

            override fun onMessage(message: String) {
                Log.d("MainActivity", "Received message: $message")
                runOnUiThread {
                    handleMessage(message)
                }
            }

            override fun onMessage(bytes: java.nio.ByteBuffer?) {
                // No binary messages expected
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w("MainActivity", "WebSocket closed: code=$code, reason=$reason, remote=$remote")
                isWebSocketOpen = false
                runOnUiThread {
                    if (isRecording || audioRecord != null) {
                        stopRecording(fromServer = true)
                    }
                    if (!reason.isNullOrBlank()) {
                        showToast(getString(R.string.status_error, reason))
                    } else if (tvOutput.text.isNullOrBlank()) {
                        tvOutput.text = getString(R.string.status_disconnected)
                    }
                }
            }

            override fun onError(ex: Exception) {
                Log.e("MainActivity", "WebSocket error", ex)
                isWebSocketOpen = false
                runOnUiThread {
                    showToast(getString(R.string.status_error, ex.message ?: "Unknown error"))
                    stopRecording(fromServer = true)
                }
            }
        }

        webSocketClient?.connect()

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("MainActivity", "Failed to initialize AudioRecord")
            showToast(getString(R.string.status_error, "Audio input not available"))
            audioRecord?.release()
            audioRecord = null
            translationEnabled = false
            isRecording = false
            isWebSocketOpen = false
            try {
                webSocketClient?.close()
            } catch (_: Exception) {
                // Ignore socket errors during shutdown
            }
            webSocketClient = null
            btnRecord.text = getString(R.string.start_button_start)
            return
        }

        try {
            audioRecord?.startRecording()
            Log.i("MainActivity", "Audio recording started")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start audio recording", e)
            showToast(getString(R.string.status_error, "Failed to start recording"))
            audioRecord?.release()
            audioRecord = null
            return
        }
        isRecording = true

        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: 0
                if (read > 0) {
                    Log.d("MainActivity", "Read $read audio samples")
                    if (isWebSocketOpen) {
                        val bytes = ByteArray(read * 2)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buffer, 0, read)
                        try {
                            webSocketClient?.send(bytes)
                            Log.d("MainActivity", "Sent $read audio bytes")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to send audio data", e)
                            val shouldNotify = isWebSocketOpen
                            isRecording = false
                            withContext(Dispatchers.Main) {
                                stopRecording(fromServer = true)
                                if (shouldNotify) {
                                    showToast(getString(R.string.status_error, e.message ?: "Connection lost"))
                                }
                            }
                        }
                    } else {
                        Log.w("MainActivity", "WebSocket not open, skipping send")
                    }
                } else if (read < 0) {
                    Log.e("MainActivity", "Audio read error: $read")
                } else {
                    delay(10)
                }
            }
        }
    }

    private fun stopRecording(fromServer: Boolean = false) {
        Log.i("MainActivity", "Stopping recording, fromServer=$fromServer")
        translationEnabled = false
        isRecording = false
        isWebSocketOpen = false
        lastSourceText = ""
        lastTranslatedText = ""

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            Log.d("MainActivity", "Audio recording stopped")
        } catch (e: IllegalStateException) {
            Log.w("MainActivity", "AudioRecord stop ignored: not recording", e)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping audio recording", e)
        }
        audioRecord?.release()
        audioRecord = null

        if (!fromServer) {
            try {
                webSocketClient?.send("END_OF_AUDIO")
                Log.d("MainActivity", "Sent END_OF_AUDIO")
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to send END_OF_AUDIO", e)
            }
            try {
                webSocketClient?.close()
                Log.d("MainActivity", "WebSocket closed by client")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error closing WebSocket", e)
            }
        }

        webSocketClient = null
        btnRecord.text = getString(R.string.start_button_start)
    }

    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            when {
                json.has("status") -> {
                    val status = json.getString("status")
                    val msg = json.optString("message", "")
                    Log.d("MainActivity", "Status message: $status, msg: $msg")
                    when (status) {
                        "SERVER_READY" -> tvOutput.text = getString(R.string.status_ready)
                        "WAIT" -> {
                            if (msg.isNotBlank()) {
                                showToast(getString(R.string.toast_server_busy_with_message, msg))
                            } else {
                                showToast(getString(R.string.toast_server_busy))
                            }
                        }
                        "ERROR" -> {
                            Log.e("MainActivity", "Server error: $msg")
                            showToast(getString(R.string.status_error, msg))
                        }
                        "WARNING" -> {
                            Log.w("MainActivity", "Server warning: $msg")
                            if (msg.isNotBlank()) showToast(msg)
                        }
                    }
                }
                json.has("translated_segments") -> {
                    val segments = json.getJSONArray("translated_segments")
                    val pieces = mutableListOf<String>()
                    for (i in 0 until segments.length()) {
                        val segText = segments.getJSONObject(i).optString("text").trim()
                        if (segText.isNotEmpty()) {
                            pieces.add(segText)
                        }
                    }
                    val text = pieces.joinToString(separator = "\n\n")
                    if (text.isNotEmpty()) {
                        Log.d("MainActivity", "Translated text: $text")
                        lastTranslatedText = text
                        tvOutput.text = text
                    }
                }
                json.has("segments") -> {
                    val segments = json.getJSONArray("segments")
                    val pieces = mutableListOf<String>()
                    for (i in 0 until segments.length()) {
                        val segText = segments.getJSONObject(i).optString("text").trim()
                        if (segText.isNotEmpty()) {
                            pieces.add(segText)
                        }
                    }
                    val text = pieces.joinToString(separator = "\n\n")
                    if (text.isNotEmpty() && !translationEnabled) {
                        Log.d("MainActivity", "Transcription text: $text")
                        tvOutput.text = text
                    } else if (text.isNotEmpty()) {
                        lastSourceText = text
                        if (tvOutput.text.isNullOrBlank() || isStatusMessage(tvOutput.text.toString()) || tvOutput.text.toString() == lastSourceText || lastTranslatedText.isBlank()) {
                            Log.d("MainActivity", "Source text: $text")
                            tvOutput.text = text
                        }
                    }
                }
                message == "DISCONNECT" -> {
                    Log.i("MainActivity", "Disconnect message received")
                    showToast(getString(R.string.toast_disconnected))
                    stopRecording(fromServer = true)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling message: $message", e)
            tvOutput.append("\n\n${getString(R.string.status_error, message)}")
        }
    }

    private fun isStatusMessage(current: String): Boolean {
        val statuses = setOf(
            getString(R.string.output_placeholder),
            getString(R.string.status_connecting),
            getString(R.string.status_listening),
            getString(R.string.status_ready),
            getString(R.string.status_disconnected)
        )
        return current in statuses
    }

    private fun showToast(message: String) {
        Log.d("MainActivity", "Showing toast: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Log.i("MainActivity", "Activity destroying")
        super.onDestroy()
        stopRecording(fromServer = false)
    }
}
