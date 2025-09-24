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

## Building and Publishing

### Local Build

1. Ensure Android Studio is installed and open the project.
2. Sync Gradle.
3. For debug APK: `./gradlew assembleDebug` (install via ADB or Studio).
4. For release APK: Set up signing (see below), then `./gradlew assembleRelease`.
5. For App Bundle (recommended for Play Store): `./gradlew bundleRelease`.

### Signing Setup

1. Generate a keystore: `keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias`
2. Add keystore details to `app/build.gradle` (uncomment and fill signingConfigs.release).
   - Alternatively, create `keystore.properties` in `app/`:
     ```
     storePassword=your_store_password
     keyPassword=your_key_password
     keyAlias=my-key-alias
     storeFile=path/to/my-release-key.jks
     ```
     Then in build.gradle:
     ```
     def keystoreProperties = new Properties()
     def keystorePropertiesFile = rootProject.file('keystore.properties')
     if (keystorePropertiesFile.exists()) {
         keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
     }
     signingConfigs {
         release {
             keyAlias keystoreProperties['keyAlias']
             keyPassword keystoreProperties['keyPassword']
             storeFile keystoreProperties['storeFile'] ? file(keystoreProperties['storeFile']) : null
             storePassword keystoreProperties['storePassword']
         }
     }
     ```
3. Store keystore securely (backup!).

### Publishing to Google Play

1. Create a Google Play Console account (one-time $25 fee).
2. Upload AAB to "Internal testing" track first.
3. Fill app details: title "WhisperLive Transcription", description (real-time audio to text via WhisperLive server), screenshots, icons (add high-res in res/mipmap).
4. Set up store listing, content rating, pricing (free).
5. Upload AAB, create release notes (e.g., "Initial release with real-time transcription").
6. Review and publish. For updates, increment versionCode/versionName.
7. Monitor crashes/analytics in Console.

Note: Ensure app complies with Play policies (privacy for audio recording; add privacy policy URL in store listing).

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
