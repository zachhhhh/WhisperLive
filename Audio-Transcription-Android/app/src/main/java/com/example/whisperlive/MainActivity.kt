package com.example.whisperlive

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {
    private lateinit var billingManager: BillingManager
    private lateinit var btnPause: Button
    private lateinit var btnClear: Button
    private lateinit var spinnerModel: Spinner

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var switchTranslation: Switch
    private lateinit var spinnerLanguage: Spinner
    private lateinit var btnRecord: Button
    private lateinit var tvTranscription: TextView
    private lateinit var tvTranslation: TextView
    private lateinit var translationLayout: LinearLayout
    private lateinit var premiumBtn: Button

    private var webSocketClient: WebSocketClient? = null
    private var isWebSocketOpen = false
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val languages = arrayOf("en", "fr", "es", "hi", "ja", "de", "it", "pt", "zh", "ar", "ru", "ko")
    private val languageNames = arrayOf("English", "French", "Spanish", "Hindi", "Japanese", "German", "Italian", "Portuguese", "Chinese", "Arabic", "Russian", "Korean")
    private val models = arrayOf("tiny", "base", "small", "medium", "large")

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Monetization setup
        billingManager = BillingManager(this)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        switchTranslation = findViewById(R.id.switchTranslation)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        btnRecord = findViewById(R.id.btnRecord)
        tvTranscription = findViewById(R.id.tvTranscription)
        tvTranslation = findViewById(R.id.tvTranslation)
        translationLayout = findViewById(R.id.translationLayout)
        premiumBtn = findViewById(R.id.premiumBtn)
        btnPause = findViewById(R.id.btnPause)
        btnClear = findViewById(R.id.btnClear)
        spinnerModel = findViewById(R.id.spinnerModel)
        premiumBtn.isEnabled = false
        premiumBtn.text = getString(R.string.premium_button_loading)
        premiumBtn.setOnClickListener {
            billingManager.launchBillingFlow(this, billingManager.productDetails.value.firstOrNull() ?: return@setOnClickListener)
        }

        // Load saved settings
        etHost.setText(sharedPreferences.getString("host", "localhost"))
        etPort.setText(sharedPreferences.getString("port", "9090"))
        val savedModelIndex = sharedPreferences.getInt("model_index", 2)
        spinnerModel.setSelection(savedModelIndex)
        val savedTranslation = sharedPreferences.getBoolean("translation_enabled", false)
        switchTranslation.isChecked = savedTranslation
        if (savedTranslation) {
            spinnerLanguage.visibility = View.VISIBLE
            translationLayout.visibility = View.VISIBLE
        }
        val savedLanguageIndex = sharedPreferences.getInt("language_index", 0)
        spinnerLanguage.setSelection(savedLanguageIndex)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter
        spinnerLanguage.setSelection(0) // Default English
        spinnerModel.setSelection(2) // Default "small"

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

        btnPause.setOnClickListener {
            if (isRecording) {
                if (isPaused) {
                    resumeRecording()
                } else {
                    pauseRecording()
                }
            }
        }

        btnClear.setOnClickListener {
            tvTranscription.text = "Transcription will appear here..."
            tvTranslation.text = "Translation will appear here..."
        }

        // Premium check
        lifecycleScope.launch {
            billingManager.purchaseState.collectLatest { state ->
                if (state == PurchaseState.Purchased) {
                    premiumBtn.visibility = View.GONE
                } else {
                    premiumBtn.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            billingManager.productDetails.collectLatest { details ->
                val premiumDetails = details.firstOrNull()
                if (premiumDetails != null) {
                    val price = premiumDetails.oneTimePurchaseOfferDetails?.formattedPrice
                    premiumBtn.isEnabled = true
                    premiumBtn.text = price?.let {
                        getString(R.string.premium_button_with_price, it)
                    } ?: getString(R.string.premium_button_default)
                } else {
                    premiumBtn.isEnabled = false
                    premiumBtn.text = getString(R.string.premium_button_loading)
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

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val host = etHost.text.toString().trim()
        val portStr = etPort.text.toString().trim()

        // Save settings
        with(sharedPreferences.edit()) {
            putString("host", host)
            putString("port", portStr)
            putInt("model_index", spinnerModel.selectedItemPosition)
            putBoolean("translation_enabled", switchTranslation.isChecked)
            putInt("language_index", spinnerLanguage.selectedItemPosition)
            apply()
        }
        if (host.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "Please enter host and port", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull() ?: 9090
        val enableTranslation = switchTranslation.isChecked
        val targetLanguage = languages[spinnerLanguage.selectedItemPosition]
        val model = models[spinnerModel.selectedItemPosition]

        tvTranscription.text = "Connecting..."
        tvTranslation.text = "Translation will appear here..."

        // Generate UID
        val uid = java.util.UUID.randomUUID().toString()

        // Initial JSON
        val json = JSONObject().apply {
            put("uid", uid)
            put("language", "en")
            put("task", "transcribe")
            put("model", model)
            put("use_vad", true)
            put("enable_translation", enableTranslation)
            put("target_language", targetLanguage)
            put("send_last_n_segments", 10)
        }

        val uri = URI("ws://$host:$port")
        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshake: ServerHandshake) {
                isWebSocketOpen = true
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

            override fun onMessage(bytes: ByteBuffer?) {
                // Binary audio response not expected
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                isWebSocketOpen = false
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Disconnected: $reason", Toast.LENGTH_SHORT).show()
                    tvTranscription.text = "Disconnected"
                }
            }

            override fun onError(ex: Exception) {
                isWebSocketOpen = false
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
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        btnRecord.text = "Stop Recording"
        btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        btnPause.visibility = View.VISIBLE
        btnPause.text = "Pause"

        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            val buffer = FloatArray(BUFFER_SIZE)
            while (isRecording) {
                if (!isPaused) {
                    if (!isWebSocketOpen) {
                        delay(50)
                        continue
                    }
                    val read = audioRecord?.read(buffer, 0, BUFFER_SIZE, AudioRecord.READ_BLOCKING) ?: 0
                    if (read > 0) {
                        val bytes = ByteArray(read * 4) // Float32
                        for (index in 0 until read) {
                            val value = buffer[index]
                            val bytesOffset = index * 4
                            val intBits = java.lang.Float.floatToIntBits(value)
                            bytes[bytesOffset] = (intBits and 0xff).toByte()
                            bytes[bytesOffset + 1] = ((intBits shr 8) and 0xff).toByte()
                            bytes[bytesOffset + 2] = ((intBits shr 16) and 0xff).toByte()
                            bytes[bytesOffset + 3] = ((intBits shr 24) and 0xff).toByte()
                        }
                        try {
                            webSocketClient?.send(bytes)
                        } catch (e: Exception) {
                            // Exit the loop gracefully if the socket is no longer available
                            val shouldNotify = isWebSocketOpen
                            isRecording = false
                            withContext(Dispatchers.Main) {
                                stopRecording()
                                if (shouldNotify) {
                                    Toast.makeText(this@MainActivity, "Connection lost", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } else {
                    delay(100)
                }
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording && audioRecord == null) {
            return
        }
        isRecording = false
        isWebSocketOpen = false
        isPaused = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // AudioRecord was not recording; nothing to stop.
        }
        audioRecord?.release()
        audioRecord = null
        try {
            if (webSocketClient?.isOpen == true) {
                webSocketClient?.send("END_OF_AUDIO")
            }
        } catch (_: Exception) {
            // Socket already closed
        }
        webSocketClient?.close()
        webSocketClient = null
        btnRecord.text = "Start Recording"
        btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        btnPause.visibility = View.GONE
        tvTranscription.append("\n--- End of Recording ---")
        recordingJob = null
    }

    private fun pauseRecording() {
        isPaused = true
        btnPause.text = "Resume"
    }

    private fun resumeRecording() {
        isPaused = false
        btnPause.text = "Pause"
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
        billingManager.destroy()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }
}
