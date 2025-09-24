const { electronAPI } = window;

let mediaRecorder;
let audioContext;
let source;
let processor;
let wsConnected = false;
let isRecording = false;
let isPaused = false;
let resampler;

const hostInput = document.getElementById("host");
const portInput = document.getElementById("port");
const enableTranslationCheckbox = document.getElementById("enableTranslation");
const targetLanguageSelect = document.getElementById("targetLanguage");
const connectBtn = document.getElementById("connectBtn");
const statusDiv = document.getElementById("status");
const recordBtn = document.getElementById("recordBtn");
const pauseBtn = document.getElementById("pauseBtn");
const stopBtn = document.getElementById("stopBtn");
const transcriptionDiv = document.getElementById("transcription");
const translationDiv = document.getElementById("translation");
const translationSection = document.getElementById("translationSection");

enableTranslationCheckbox.addEventListener("change", () => {
  targetLanguageSelect.style.display = enableTranslationCheckbox.checked
    ? "block"
    : "none";
  translationSection.style.display = enableTranslationCheckbox.checked
    ? "block"
    : "none";
});

connectBtn.addEventListener("click", async () => {
  const host = hostInput.value.trim();
  const port = parseInt(portInput.value);
  const enableTranslation = enableTranslationCheckbox.checked;
  const targetLanguage = targetLanguageSelect.value;

  if (!host || isNaN(port)) {
    alert("Please enter valid host and port");
    return;
  }

  try {
    statusDiv.textContent = "Connecting...";
    connectBtn.disabled = true;
    connectBtn.textContent = "Connecting...";

    await electronAPI.connectWebSocket(
      host,
      port,
      enableTranslation,
      targetLanguage
    );

    wsConnected = true;
    statusDiv.textContent = "Connected";
    connectBtn.textContent = "Disconnect";
    connectBtn.style.background = "#28a745";
    recordBtn.disabled = false;
  } catch (error) {
    statusDiv.textContent = `Error: ${error.message}`;
    connectBtn.disabled = false;
    connectBtn.textContent = "Connect to Server";
    connectBtn.style.background = "#007bff";
  }
});

recordBtn.addEventListener("click", async () => {
  if (!wsConnected) return;

  if (isRecording) {
    pauseRecording();
  } else {
    startRecording();
  }
});

pauseBtn.addEventListener("click", () => {
  if (isRecording) {
    if (isPaused) {
      resumeRecording();
    } else {
      pauseRecording();
    }
  }
});

stopBtn.addEventListener("click", () => {
  stopRecording();
});

function startRecording() {
  navigator.mediaDevices
    .getUserMedia({ audio: true })
    .then((stream) => {
      audioContext = new AudioContext({ sampleRate: 16000 });
      source = audioContext.createMediaStreamSource(stream);
      processor = audioContext.createScriptProcessor(4096, 1, 1);

      // Resample if needed (browser sample rate to 16kHz)
      const inputSampleRate = audioContext.sampleRate;
      if (inputSampleRate !== 16000) {
        resampler = audioContext.createBiquadFilter(); // Simple resampler not implemented; assume browser supports or use lib
        // For simplicity, assume 16kHz or implement basic resample
        source.connect(resampler);
        resampler.connect(processor);
      } else {
        source.connect(processor);
      }

      processor.onaudioprocess = (e) => {
        if (isRecording && !isPaused) {
          const inputBuffer = e.inputBuffer.getChannelData(0);
          const float32Array = new Float32Array(inputBuffer);
          const audioData = float32Array.buffer;
          electronAPI.sendAudio(audioData);
        }
      };

      processor.connect(audioContext.destination); // Optional: play back

      isRecording = true;
      isPaused = false;
      recordBtn.textContent = "Pause Recording";
      recordBtn.classList.remove("record-btn");
      recordBtn.classList.add("pause-btn");
      pauseBtn.style.display = "none";
      stopBtn.style.display = "block";
      transcriptionDiv.textContent = "Listening...";
    })
    .catch((err) => {
      alert("Error accessing microphone: " + err.message);
    });
}

function pauseRecording() {
  isPaused = true;
  recordBtn.textContent = "Resume Recording";
  recordBtn.classList.remove("pause-btn");
  recordBtn.classList.add("record-btn");
}

function resumeRecording() {
  isPaused = false;
  recordBtn.textContent = "Pause Recording";
  recordBtn.classList.remove("record-btn");
  recordBtn.classList.add("pause-btn");
}

function stopRecording() {
  isRecording = false;
  isPaused = false;
  if (processor) {
    processor.disconnect();
  }
  if (source) {
    source.disconnect();
  }
  if (audioContext) {
    audioContext.close();
  }

  electronAPI.disconnectWebSocket();

  recordBtn.textContent = "Start Recording";
  recordBtn.classList.remove("pause-btn", "stop-btn");
  recordBtn.classList.add("record-btn");
  recordBtn.disabled = true;
  pauseBtn.style.display = "none";
  stopBtn.style.display = "none";
  transcriptionDiv.textContent += "\n--- End of Recording ---";
}

electronAPI.onServerReady(() => {
  statusDiv.textContent = "Server Ready";
  recordBtn.disabled = false;
  recordBtn.textContent = "Start Recording";
});

electronAPI.onTranscription((segments) => {
  let text = "";
  segments.forEach((seg) => {
    text += seg.text + " ";
  });
  transcriptionDiv.textContent += "\n" + text.trim();
  transcriptionDiv.scrollTop = transcriptionDiv.scrollHeight;
});

electronAPI.onTranslation((segments) => {
  let text = "";
  segments.forEach((seg) => {
    text += seg.text + " ";
  });
  translationDiv.textContent += "\n" + text.trim();
  translationDiv.scrollTop = translationDiv.scrollHeight;
});

electronAPI.onDisconnected(() => {
  wsConnected = false;
  statusDiv.textContent = "Disconnected";
  connectBtn.textContent = "Connect to Server";
  connectBtn.disabled = false;
  connectBtn.style.background = "#007bff";
  recordBtn.disabled = true;
  stopRecording(); // If recording
});
