# Audio-Transcription-iOS

This is an iOS client for [WhisperLive](https://github.com/collabora/WhisperLive), a real-time speech-to-text server based on OpenAI Whisper.  
The app streams microphone audio to a WhisperLive server via WebSocket and displays live transcription results in real time.

> ⚠️ This client is designed to work specifically with the [WhisperLive Python WebSocket server](https://github.com/collabora/WhisperLive?tab=readme-ov-file#running-the-server).  
> Make sure the server is running and reachable from your iOS device.

## Features

- Real-time microphone capture with AVAudioEngine
- Streaming to WhisperLive backend using WebSocket
- Displays transcription as segments arrive
- Start / Pause / Resume / Stop recording with SwiftUI interface
- Final transcription view on stop

## Requirements

- iOS 15.0+
- Swift 5.8+
- AVFoundation (for microphone)
- Working WhisperLive WebSocket server

## Project Setup (No Existing .xcodeproj)

This directory contains the source files for the iOS app. There is no pre-built Xcode project file. Follow these steps to create one:

1. Open Xcode and create a new project:

   - File > New > Project
   - Select iOS > App
   - Interface: SwiftUI
   - Language: Swift
   - Name: "WhisperLive-iOS-Client"
   - Organization Identifier: "com.example" (or your own, e.g., "com.yourname.whisperlive")
   - Bundle Identifier will auto-generate (e.g., com.example.WhisperLive-iOS-Client)
   - Deployment Target: iOS 15.0
   - Uncheck "Use Core Data" and "Include Tests"

2. Replace default files with the provided ones:

   - Delete `ContentView.swift` and replace with the provided [`ContentView.swift`](ContentView.swift) (contains RecordingView and FinalTranscriptView).
   - Delete `YourAppNameApp.swift` and replace with [`WhisperLive_iOS_ClientApp.swift`](WhisperLive_iOS_ClientApp.swift).
   - Add new files: Right-click project > New File > Swift File
     - [`RecordingViewModel.swift`](RecordingViewModel.swift)
     - [`AudioWebSocket.swift`](AudioWebSocket.swift)
     - [`AudioStream.swift`](AudioStream.swift) (note: renamed from AudioStreamer.swift in code references)
   - Replace `Info.plist` with the provided [`WhisperLive-iOS-Client-Info.plist`](WhisperLive-iOS-Client-Info.plist) (includes NSMicrophoneUsageDescription).

3. Configure project settings:

   - Select project in navigator > Targets > WhisperLive-iOS-Client > General
     - Version: 1.0.0
     - Build: 1
     - Deployment Info: iOS 15.0
   - Signing & Capabilities:
     - Check "Automatically manage signing"
     - Select your Team (see "Running on Physical Device" below for free setup)
     - Add Capability: "App Sandbox" if needed, but mic is handled via plist.
   - Info tab: Ensure NSMicrophoneUsageDescription is present.

4. Build and run: Cmd+R on simulator or device.

## Getting Started (After Setup)

1. Clone the repository (your fork):

   ```bash
   git clone https://github.com/yourusername/whisperlive.git
   cd whisperlive/Audio-Transcription-iOS
   ```

2. Follow Project Setup above to create/open in Xcode.

3. Add the following to your `Info.plist`:

   ```xml
   <key>NSMicrophoneUsageDescription</key>
   <string>This app requires microphone access for transcription.</string>
   ```

4. Run the app on a physical device (recommended)

## Running on a Physical Device (with Free Apple ID)

You can run this app on a real iPhone without a paid Apple Developer account. Follow these steps:

### 1. Register a Free Apple ID in Xcode

1. Open Xcode ▸ Settings… (or Preferences) ▸ **Accounts**
2. Click the **+** button ▸ Select **Apple ID**
3. Sign in with your Apple ID (a free one is fine)
4. A "Personal Team" will be created automatically

> ✅ You can deploy up to 3 apps on a physical device using a free Apple ID with a 7-day provisioning profile.

---

### 2. Set Up Signing in Your Project

1. In Xcode, select your **project** in the Project Navigator
2. Go to **TARGETS ▸ YourAppName ▸ Signing & Capabilities**
3. Set **Team** to your Personal Team
4. Set a unique **Bundle Identifier** (e.g., `com.yourname.whisperlive`)
5. Make sure **Automatically manage signing** is checked
6. If a red warning appears, click **"Resolve Issues"**

---

### 3. Connect and Trust Your iPhone

1. Connect your iPhone via USB
2. When prompted, tap **“Trust This Computer”** on your iPhone
3. Make sure your iPhone appears in Xcode's device list

---

### 4. Enable Developer Mode on iPhone

1. Press the **Build (▶︎)** button in Xcode
2. Your iPhone will ask to enable **Developer Mode**
3. On iPhone, go to:  
   **Settings ▸ Privacy & Security ▸ Developer Mode**
4. Enable it and restart the device if required

---

Now you can run and debug the app on your real device!

## Building and Publishing

### Local Build and Run

- Use Xcode: Cmd+B to build, Cmd+R to run on simulator/device.
- For physical device: Follow "Running on a Physical Device" section below.
- Server config: Enter host/port in app UI (defaults: localhost:9090).

### Signing Setup for Publishing

1. Enroll in Apple Developer Program ($99/year): https://developer.apple.com/programs/enroll/
2. In Xcode: Project > Signing & Capabilities > Select your paid Team.
3. Bundle ID: Use reverse-domain (e.g., com.yourname.whisperlive) – register in developer portal if needed.
4. Certificates: Xcode auto-manages, but download Distribution certificate if issues.
5. Provisioning Profile: Auto for development; for App Store, use "App Store" profile.

### Publishing to App Store

1. Create App in App Store Connect: https://appstoreconnect.apple.com/

   - New App > iOS > Name: "WhisperLive Transcription"
   - Bundle ID: Match Xcode (e.g., com.yourname.whisperlive)
   - SKU: Unique (e.g., WHISPERLIVE001)
   - Version 1.0

2. Prepare assets:

   - App Icon: Add 1024x1024 PNG to Assets.xcassets (create if missing).
   - Screenshots: iPhone 6.5", 5.5" displays.
   - Description: "Real-time audio transcription using WhisperLive server. Streams mic to server for live text output with optional translation."
   - Keywords: whisper, transcription, speech-to-text, real-time
   - Privacy: Add policy URL (e.g., GitHub README) for mic access.
   - Category: Utilities

3. Archive and Upload:

   - Product > Archive (use Generic iOS Device).
   - In Organizer: Select archive > Distribute App > App Store Connect > Upload.
   - Wait for processing in App Store Connect.

4. Submit for Review:

   - In App Store Connect: Prepare for Submission > Add build > Fill metadata > Submit.
   - Review takes 1-2 days; respond to any rejections (e.g., server dependency disclosure).
   - Once approved, release manually or auto.

5. Updates: Increment version/build in Xcode, re-archive/upload.

Note: App requires internet/server; disclose in description. Comply with App Review Guidelines (privacy, no backend-only apps).

## Running on a Physical Device (with Free Apple ID)

You can run this app on a real iPhone without a paid Apple Developer account. Follow these steps:

### 1. Register a Free Apple ID in Xcode

1. Open Xcode ▸ Settings… (or Preferences) ▸ **Accounts**
2. Click the **+** button ▸ Select **Apple ID**
3. Sign in with your Apple ID (a free one is fine)
4. A "Personal Team" will be created automatically

> ✅ You can deploy up to 3 apps on a physical device using a free Apple ID with a 7-day provisioning profile.

---

### 2. Set Up Signing in Your Project

1. In Xcode, select your **project** in the Project Navigator
2. Go to **TARGETS ▸ YourAppName ▸ Signing & Capabilities**
3. Set **Team** to your Personal Team
4. Set a unique **Bundle Identifier** (e.g., `com.yourname.whisperlive`)
5. Make sure **Automatically manage signing** is checked
6. If a red warning appears, click **"Resolve Issues"**

---

### 3. Connect and Trust Your iPhone

1. Connect your iPhone via USB
2. When prompted, tap **“Trust This Computer”** on your iPhone
3. Make sure your iPhone appears in Xcode's device list

---

### 4. Enable Developer Mode on iPhone

1. Press the **Build (▶︎)** button in Xcode
2. Your iPhone will ask to enable **Developer Mode**
3. On iPhone, go to:
   **Settings ▸ Privacy & Security ▸ Developer Mode**
4. Enable it and restart the device if required

---

Now you can run and debug the app on your real device!

## Folder Structure

```
Audio-Transcription-iOS/
├── ContentView.swift (contains RecordingView and FinalTranscriptView)
├── RecordingViewModel.swift
├── AudioStream.swift
├── AudioWebSocket.swift
├── WhisperLive_iOS_ClientApp.swift
├── WhisperLive-iOS-Client-Info.plist
├── README.md
```

## License

MIT  
This iOS client is provided as an open-source example to complement WhisperLive's real-time transcription ecosystem.
