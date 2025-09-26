//
//  RecordingViewModel.swift
//  Lecture2Quiz
//
//  Created by ParkMazorika on 4/27/25.
//

import AVFoundation
import Combine

/// Represents a segment of transcribed audio with start/end timestamps and completion flag.
struct TranscriptionSegment: Identifiable, Equatable {
    var id = UUID()
    var start: Double
    var end: Double
    var text: String
    var completed: Bool
}

/// ViewModel responsible for managing audio recording and transcription logic.
class AudioViewModel: ObservableObject {
    @Published var host: String = "localhost"
    @Published var port: String = "9090"
    @Published var isRecording = false            // Indicates if recording is active
    @Published var isPaused = false               // Indicates if recording is currently paused
    @Published var timeLabel = "00:00"            // Timer label formatted as mm:ss
    @Published var transcriptionList: [String] = []  // Live transcription output
    @Published var isLoading = false              // True while waiting for server response
    @Published var finalScript: String = ""       // Final script from completed segments
    @Published var enableTranslation: Bool = true
    @Published var targetLanguage: String = "es"  // Default to Spanish for translation testing
    @Published var translationSelection: String = "es" {
        didSet {
            targetLanguage = translationSelection
        }
    }
    @Published var translatedList: [String] = []     // Live translated transcription output
    @Published var finalTranslatedScript: String = "" // Final translated script
    @Published var statusBanner: String? = nil        // Latest status/warning message from server
    @Published var isLanguageSheetVisible: Bool = false

    private var timer: Timer?
    private var connectionTimeoutTimer: Timer?
    private var elapsedTime: Int = 0

    private var audioStreamer: AudioStreamer?     // Handles audio capture and streaming
    private var audioWebSocket: AudioWebSocket?   // Manages WebSocket communication

    private var segments: [TranscriptionSegment] = []  // Stores all transcription segments
    private var translatedSegments: [TranscriptionSegment] = []  // Stores translated segments

    init() {}

    var availableLanguages: [LanguageOption] {
        LanguageCatalog.all.sorted { $0.name < $1.name }
    }

    var selectedLanguageName: String {
        LanguageCatalog.name(for: targetLanguage)
    }

    var transcriptionText: String {
        transcriptionList.joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var translationText: String {
        translatedList.joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Starts audio recording and initializes WebSocket + AVAudioEngine.
    func startRecording() {
        let portInt = Int(port) ?? 9090
        audioWebSocket = AudioWebSocket(
            host: host,
            port: portInt,
            enableTranslation: enableTranslation,
            targetLanguage: targetLanguage
        )
        audioStreamer = AudioStreamer(webSocket: audioWebSocket!)

        transcriptionList.removeAll()
        translatedList.removeAll()
        segments.removeAll()
        translatedSegments.removeAll()
        finalScript = ""
        finalTranslatedScript = ""

        isLoading = true
        loadingProgress = 0.0
        statusBanner = nil
        audioWebSocket?.onStatusMessage = { [weak self] status, message in
            guard let self = self else { return }
            DispatchQueue.main.async {
                switch status.uppercased() {
                case "WARNING", "ERROR":
                    self.statusBanner = message ?? status
                case "WAIT":
                    if let message = message {
                        self.statusBanner = "Server busy: \(message)"
                    } else {
                        self.statusBanner = "Server busy. Please wait..."
                    }
                default:
                    self.statusBanner = nil
                }
            }
        }

        audioWebSocket?.onConnectionError = { [weak self] errorMsg in
            DispatchQueue.main.async {
                self?.statusBanner = errorMsg
                self?.isLoading = false
                self?.loadingTimer?.invalidate()
                self?.loadingTimer = nil
                self?.connectionTimeoutTimer?.invalidate()
                self?.connectionTimeoutTimer = nil
            }
        }

        // Start loading progress timer
        loadingTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            self.loadingProgress = min(1.0, self.loadingProgress + 0.02)  // Simulate 5-second load
        }

        connectionTimeoutTimer = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: false) { _ in
            DispatchQueue.main.async {
                if !self.isRecording {
                    self.statusBanner = "Connection timeout. Please check if the server is running and try again."
                    self.isLoading = false
                    self.loadingTimer?.invalidate()
                    self.loadingTimer = nil
                }
            }
        }

        // Handle server transcription message
        audioWebSocket?.onTranscriptionReceived = { [weak self] text in
            self?.handleRawTranscriptionJSON(text)
        }

        // When server sends SERVER_READY
        audioWebSocket?.onServerReady = { [weak self] in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.loadingTimer?.invalidate()
                self.loadingTimer = nil
                self.loadingProgress = 1.0
                self.connectionTimeoutTimer?.invalidate()
                self.connectionTimeoutTimer = nil
                self.isLoading = false
                self.isRecording = true
                self.isPaused = false
                self.timeLabel = "00:00"
                self.elapsedTime = 0
                self.startTimer()
                self.audioStreamer?.startStreaming()
                self.statusBanner = nil
            }
        }
    }

    /// Pauses the recording and stops the timer.
    func pauseRecording() {
        isPaused = true
        audioStreamer?.pauseStreaming()
        timer?.invalidate()
    }

    /// Resumes recording and restarts the timer.
    func resumeRecording() {
        isPaused = false
        audioStreamer?.resumeStreaming()
        startTimer()
    }

    /// Stops recording and finalizes connection to server.
    func stopRecording() {
        isRecording = false
        isPaused = false
        timer?.invalidate()
        loadingTimer?.invalidate()
        loadingTimer = nil
        noTranscriptionTimer?.invalidate()
        noTranscriptionTimer = nil
        connectionTimeoutTimer?.invalidate()
        connectionTimeoutTimer = nil

        audioStreamer?.stopStreaming()
        audioWebSocket?.sendEndOfAudio()
        audioWebSocket?.onTranscriptionReceived = nil
        audioWebSocket?.closeConnection()
    }

    /// Starts the recording timer (1-second interval).
    private var noTranscriptionTimer: Timer?

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            self.elapsedTime += 1
            let minutes = self.elapsedTime / 60
            let seconds = self.elapsedTime % 60
            self.timeLabel = String(format: "%02d:%02d", minutes, seconds)

            // Alert if no transcription after 10 seconds
            if self.elapsedTime >= 10 && self.transcriptionList.isEmpty && self.isRecording {
                self.statusBanner = "No speech detected. Check microphone or try speaking louder."
                self.noTranscriptionTimer?.invalidate()
            }
        }

        // Start no-transcription alert timer
        noTranscriptionTimer = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: false) { _ in
            if self.transcriptionList.isEmpty && self.isRecording {
                DispatchQueue.main.async {
                    self.statusBanner = "No speech detected. Check microphone or try speaking louder."
                }
            }
        }
    }

    /// Finalizes the transcription by joining all completed segments into one string.
    func finalizeTranscription() {
        isLoading = false
        let completedText = segments
            .filter { $0.completed }
            .map { String($0.text).trimmingCharacters(in: .whitespaces) }
            .joined(separator: " ")
        finalScript = completedText

        let completedTranslatedText = translatedSegments
            .filter { $0.completed }
            .map { String($0.text).trimmingCharacters(in: .whitespaces) }
            .joined(separator: " ")
        finalTranslatedScript = completedTranslatedText
        if !finalTranslatedScript.isEmpty {
            print("Final translated transcript:\n\(finalTranslatedScript)")
        }

        print("Final transcript:\n\(finalScript)")
    }

    /// Handles incoming JSON from the server and updates UI state.
    /// Supports both full JSON and raw string cases.
    func handleRawTranscriptionJSON(_ jsonString: String) {
        let trimmed = jsonString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = trimmed.data(using: .utf8) else { return }

        if trimmed.hasPrefix("{") {
            // Parse JSON containing segment list
            do {
                if let dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    if let segmentDicts = dict["segments"] as? [[String: Any]] {
                        processSegments(segmentDicts, isTranslated: false)
                    } else if let translatedDicts = dict["translated_segments"] as? [[String: Any]] {
                        processSegments(translatedDicts, isTranslated: true)
                    }
                }
            } catch {
                print("JSON parsing error: \(error)")
            }
        } else {
            // Handle raw text line (fallback for transcription)
            DispatchQueue.main.async {
                if self.transcriptionList.last != trimmed {
                    self.transcriptionList.append(trimmed)
                    self.finalScript = self.transcriptionList.joined(separator: " ")
                }
            }
        }
    }

    private func processSegments(_ segmentDicts: [[String: Any]], isTranslated: Bool) {
        var targetSegments: [TranscriptionSegment] = isTranslated ? translatedSegments : segments

        for item in segmentDicts {
            let start = item["start"] as? Double ?? 0.0
            let end = item["end"] as? Double ?? 0.0
            let text = (item["text"] as? String) ?? String(describing: item["text"] ?? "")
            let completed = item["completed"] as? Bool ?? false

            let newSegment = TranscriptionSegment(start: start, end: end, text: text, completed: completed)

            // Overwrite if already exists, else append
            if let index = targetSegments.firstIndex(where: { $0.start == start }) {
                targetSegments[index] = newSegment
            } else {
                targetSegments.append(newSegment)
            }
        }

        // Update the UI
        DispatchQueue.main.async {
            if isTranslated {
                self.translatedSegments = targetSegments
                let completedTranslatedTexts = self.translatedSegments
                    .filter { $0.completed }
                    .sorted(by: { $0.start < $1.start })
                    .map { String($0.text).trimmingCharacters(in: .whitespaces) }

                let pendingTranslatedText = self.translatedSegments
                    .filter { !$0.completed }
                    .sorted(by: { $0.start < $1.start })
                    .map { String($0.text).trimmingCharacters(in: .whitespaces) }
                    .last ?? ""

                self.translatedList = completedTranslatedTexts + (pendingTranslatedText.isEmpty ? [] : [pendingTranslatedText])
                self.finalTranslatedScript = self.translatedList.joined(separator: " ")
            } else {
                self.segments = targetSegments
                let completedTexts = self.segments
                    .filter { $0.completed }
                    .sorted(by: { $0.start < $1.start })
                    .map { String($0.text).trimmingCharacters(in: .whitespaces) }

                let pendingText = self.segments
                    .filter { !$0.completed }
                    .sorted(by: { $0.start < $1.start })
                    .map { String($0.text).trimmingCharacters(in: .whitespaces) }
                    .last ?? ""

                self.transcriptionList = completedTexts + (pendingText.isEmpty ? [] : [pendingText])
                self.finalScript = self.transcriptionList.joined(separator: " ")
            }
        }
    }
}
