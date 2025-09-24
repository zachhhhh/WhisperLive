# WhisperLive Desktop Client

Cross-platform Electron app for real-time audio transcription using WhisperLive server. Supports Windows and macOS.

## Features

- Microphone capture and streaming via WebSocket
- Real-time transcription display
- Optional translation with language selection
- Simple UI for server config and recording controls

## Requirements

- Node.js 18+
- Electron 28+
- Working WhisperLive server
- For Mac builds: macOS machine, Xcode, Apple Developer account ($99/year for notarization/App Store)
- For Windows builds: Windows or cross-compile setup

## Setup and Run

1. Clone repo and cd to `Desktop-Client-Windows` (now cross-platform).
2. Install dependencies: `npm install`
3. Run: `npm start`

## Building

- Windows installer (NSIS): `npm run build:win` (outputs MSIX/EXE in `dist/win-unpacked/`)
- macOS DMG: `npm run build:mac` (outputs DMG in `dist/mac/`)
- All platforms: `npm run build:all`
- Customize: Update version in package.json, add icons to `build/` (icon.ico for Win, icon.icns for Mac).

### Signing Setup

#### Windows (Microsoft Store or EV Code Signing)

1. Obtain EV Code Signing Certificate (e.g., DigiCert, ~$400/year).
2. Install cert to Windows Cert Store.
3. In package.json "win": Add "certificateFile": "path/to/cert.pfx", "certificatePassword": "password".
4. For Store: Use MSIX packaging (electron-builder supports); sign with Store cert.

#### macOS (Notarization/App Store)

1. Enroll in Apple Developer Program ($99/year).
2. Generate Developer ID Application cert in Keychain Access.
3. In package.json "mac": Set "identity": "Developer ID Application: Your Name (TEAMID)".
4. For notarization: Set "notarize" with "appleId", "appleIdPassword" (app-specific password), "teamId".
5. For App Store: Build .app, sign with Distribution cert, upload via Transporter app.

Run `npm run build:mac -- --publish=never` for local testing.

## Publishing

### Windows (Microsoft Store)

1. Create Microsoft Partner Center account ($19 one-time).
2. Reserve app name "WhisperLive Transcription".
3. Prepare: MSIX package from build, screenshots (various resolutions), description ("Real-time speech-to-text client for WhisperLive server"), privacy policy (mic/internet access).
4. Submit: Partner Center > App > Packages > Upload MSIX > Fill store listing > Certification (1-3 days).
5. Updates: New build, increment version.

### macOS (Mac App Store)

1. In App Store Connect: New App > macOS > Bundle ID match package.json appId.
2. Prepare: Signed .app from build, icons (512x512+), screenshots (MacBook/iPad if universal).
3. Upload: Use Xcode or Transporter: `xcrun altool --upload-app --file path/to/WhisperLive Client.app`.
4. Submit: App Store Connect > Prepare for Submission > Add build > Metadata (category: Utilities, disclose server dependency) > Submit for Review (1-2 weeks).
5. Notarization for direct dist: `xcrun notarytool submit path/to/dmg --keychain-profile "notary" --wait` (after build).

Note: App requires internet/server; include privacy policy URL. Comply with store guidelines (no unsigned code, disclose data collection).

## License

MIT
