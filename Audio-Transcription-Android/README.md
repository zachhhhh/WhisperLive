# Audio-Transcription-Android

This is an Android client for [WhisperLive](https://github.com/collabora/WhisperLive), a real-time speech-to-text server based on OpenAI Whisper.  
The app streams microphone audio to a WhisperLive server via WebSocket and displays live transcription results in real time, with optional translation support.

> ⚠️ This client is designed to work specifically with the [WhisperLive Python WebSocket server](https://github.com/collabora/WhisperLive?tab=readme-ov-file#running-the-server).  
> Make sure the server is running and reachable from your Android device.

## Features

- Real-time microphone capture with AudioRecord
- Streaming to WhisperLive backend using WebSocket (OkHttp)
- Displays transcription as segments arrive
- Toggle for translation with language selection
- Start / Pause / Resume / Stop recording with Material UI
- Final transcription view on stop

## Requirements

- Android 8.0+ (API 26)
- Kotlin 1.8+
- Permissions: RECORD_AUDIO, INTERNET
- Working WhisperLive WebSocket server

## Getting Started

1. Clone the repository (your fork):

   ```bash
   git clone https://github.com/yourusername/whisperlive.git
   cd whisperlive/Audio-Transcription-Android
   ```

2. Open in Android Studio

3. Sync Gradle and build the project

4. Add to AndroidManifest.xml (already included):

   ```xml
   <uses-permission android:name="android.permission.RECORD_AUDIO" />
   <uses-permission android:name="android.permission.INTERNET" />
   ```

5. Run the app on a physical device (recommended for mic access)

## Usage

- Enter server host/port in settings
- Toggle translation and select target language if desired
- Tap the mic button to start recording
- Speak clearly; transcription appears in real-time
- Tap pause/resume as needed
- Tap stop to finalize and view full transcript/translation

## License

MIT  
This Android client is provided as an open-source example to complement WhisperLive's real-time transcription ecosystem.
