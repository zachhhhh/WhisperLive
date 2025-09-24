package com.example.whisperlive

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var switchTranslation: Switch
    private lateinit var spinnerLanguage: Spinner
    private lateinit var btnRecord: Button
    private lateinit var tvTranscription: TextView
    private lateinit var tvTranslation: TextView
    private lateinit var translationLayout: LinearLayout

    private var webSocketClient: WebSocketClient? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val languages = arrayOf("en", "fr", "es", "hi", "ja")
    private val languageNames = arrayOf("English", "French", "Spanish", "Hindi", "Japanese")

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        switchTranslation = findViewById(R.id.switchTranslation)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        btnRecord = findViewById(R.id.btnRecord)
        tvTranscription = findViewById(R.id.tvTranscription)
        tvTranslation = findViewById(R.id.tvTranslation)
        translationLayout = findViewById(R.id.translationLayout)

        etHost.setText("localhost")
        etPort.setText("9090")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter
        spinnerLanguage.setSelection(0) // Default English

        switchTranslation.setOnCheckedChangeListener { _, isChecked ->
            spinnerLanguage.visibility = if (isChecked) View.VISIBLE else View.GONE
            translationLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

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

        // Default visibility
        translationLayout.visibility = View.GONE
        spinnerLanguage.visibility = View.GONE
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        val host = etHost.text.toString().trim()
        val portStr = etPort.text.toString().trim()
        if (host.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "Please enter host and port", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull() ?: 9090
        val enableTranslation = switchTranslation.isChecked
        val targetLanguage = languages[spinnerLanguage.selectedItemPosition]

        tvTranscription.text = "Connecting..."
        tvTranslation.text = "Translation will appear here..."

        // Generate UID
        val uid = java.util.UUID.randomUUID().toString()

        // Initial JSON
        val json = JSONObject().apply {
            put("uid", uid)
            put("language", "en")
            put("task", "transcribe")
            put("model", "small")
            put("use_vad", true)
            put("enable_translation", enableTranslation)
            put("target_language", targetLanguage)
            put("send_last_n_segments", 10)
        }

        val uri = URI("ws://$host:$port")
        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshake: ServerHandshake) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
                    tvTranscription.text = "Listening..."
                }
                send(json.toString())
            }

            override fun onMessage(message: String) {
                runOnUiThread {
                    handleMessage(message)
                }
            }

            override fun onMessage(bytes: ByteArray?) {
                // Binary audio response not expected
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Disconnected: $reason", Toast.LENGTH_SHORT).show()
                    tvTranscription.text = "Disconnected"
                }
            }

            override fun onError(ex: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webSocketClient?.connect()

        // Start audio recording
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "Failed to initialize AudioRecord", Toast.LENGTH_SHORT).show()
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        btnRecord.text = "Stop Recording"
        btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = FloatArray(BUFFER_SIZE)
            while (isRecording) {
                if (!isPaused) {
                    val read = audioRecord?.read(buffer, 0, BUFFER_SIZE, AudioRecord.READ_BLOCKING) ?: 0
                    if (read > 0) {
                        val bytes = ByteArray(read * 4) // Float32
                        buffer.forEachIndexed { index, value ->
                            val bytesOffset = index * 4
                            val intBits = java.lang.Float.floatToIntBits(value)
                            bytes[bytesOffset] = (intBits and 0xff).toByte()
                            bytes[bytesOffset + 1] = ((intBits shr 8) and 0xff).toByte()
                            bytes[bytesOffset + 2] = ((intBits shr 16) and 0xff).toByte()
                            bytes[bytesOffset + 3] = ((intBits shr 24) and 0xff).toByte()
                        }
                        webSocketClient?.send(bytes)
                    }
                } else {
                    delay(100)
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        isPaused = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        webSocketClient?.send("END_OF_AUDIO")
        webSocketClient?.close()
        webSocketClient = null
        btnRecord.text = "Start Recording"
        btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        tvTranscription.append("\n--- End of Recording ---")
    }

    private fun pauseRecording() {
        isPaused = true
        btnRecord.text = "Resume Recording"
    }

    private fun resumeRecording() {
        isPaused = false
        btnRecord.text = "Pause Recording"
    }

    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            when {
                json.has("status") -> {
                    val status = json.getString("status")
                    val msg = json.optString("message", "")
                    when (status) {
                        "SERVER_READY" -> {
                            tvTranscription.text = "Ready - Start speaking..."
                        }
                        "WAIT" -> Toast.makeText(this, "Server full, wait $msg min", Toast.LENGTH_SHORT).show()
                        "ERROR" -> Toast.makeText(this, "Error: $msg", Toast.LENGTH_SHORT).show()
                        "WARNING" -> Toast.makeText(this, "Warning: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
                json.has("segments") -> {
                    val segments = json.getJSONArray("segments")
                    var text = StringBuilder()
                    for (i in 0 until segments.length()) {
                        val seg = segments.getJSONObject(i)
                        val segText = seg.getString("text")
                        text.append(segText).append(" ")
                    }
                    tvTranscription.append("\n$text")
                }
                json.has("translated_segments") -> {
                    val segments = json.getJSONArray("translated_segments")
                    var text = StringBuilder()
                    for (i in 0 until segments.length()) {
                        val seg = segments.getJSONObject(i)
                        val segText = seg.getString("text")
                        text.append(segText).append(" ")
                    }
                    tvTranslation.append("\n$text")
                }
                message == "DISCONNECT" -> {
                    Toast.makeText(this, "Disconnected due to overtime", Toast.LENGTH_SHORT).show()
                    stopRecording()
                }
            }
        } catch (e: Exception) {
            tvTranscription.append("\nError parsing: $message")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
}