//
//  ContentView.swift
//  WhisperLive_iOS_Client
//
//  Created by ParkMazorika on 6/17/25.
//

import SwiftUI

/// A standalone view for recording and real-time transcription display.
struct RecordingView: View {
    var onDismiss: () -> Void
    @StateObject private var recordingViewModel = AudioViewModel()
    @StateObject private var iapManager = IAPManager()
    @State private var showSubmitView = false
    @State private var showPremiumSheet = false

    var body: some View {
        VStack(spacing: 0) {
            if let status = recordingViewModel.statusBanner {
                Text(status)
                    .font(.footnote)
                    .foregroundColor(.white)
                    .padding(8)
                    .frame(maxWidth: .infinity)
                    .background(Color.orange)
            }

            // Server config (only when not recording)
            if !recordingViewModel.isRecording {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Server Configuration")
                        .font(.headline)
                    HStack {
                        Text("Host:")
                        TextField("localhost", text: $recordingViewModel.host)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                    }
                    HStack {
                        Text("Port:")
                        TextField("9090", text: $recordingViewModel.port)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .keyboardType(.numberPad)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
                .padding(.horizontal)
                
                // Translation controls
                VStack(alignment: .leading, spacing: 8) {
                    Toggle("Enable Translation", isOn: $recordingViewModel.enableTranslation)
                        .toggleStyle(SwitchToggleStyle(tint: .blue))
                        .disabled(!iapManager.isPremium())
                    if recordingViewModel.enableTranslation {
                        Picker("Target Language", selection: $recordingViewModel.targetLanguage) {
                            Text("English").tag("en")
                            Text("French").tag("fr")
                            Text("Spanish").tag("es")
                            Text("Hindi").tag("hi")
                            Text("Japanese").tag("ja")
                        }
                        .pickerStyle(MenuPickerStyle())
                        .disabled(!iapManager.isPremium())
                    }
                    if !iapManager.isPremium() {
                        Text("Upgrade to Premium to enable translation")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
                .padding(.horizontal)

                // Premium Button (if not premium)
                if iapManager.purchaseState != .purchased {
                    Button("Go Premium") {
                        showPremiumSheet = true
                    }
                    .buttonStyle(.borderedProminent)
                    .padding()
                }
            }

            // Stop button (only visible when recording)
            HStack {
                Spacer()
                if recordingViewModel.isRecording {
                    Button("Stop Recording") {
                        recordingViewModel.stopRecording()
                        recordingViewModel.finalizeTranscription()
                        showSubmitView = true
                    }
                    .font(.headline)
                    .padding()
                    .foregroundColor(.gray)
                }
            }

            // Transcription display
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(recordingViewModel.transcriptionList.indices, id: \.self) { index in
                        Text(recordingViewModel.transcriptionList[index])
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.gray.opacity(0.1))
                            .cornerRadius(8)
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
                .padding(.horizontal)
            }

            if recordingViewModel.enableTranslation && !recordingViewModel.translatedList.isEmpty {
                Divider()
                Text("Translation (\(recordingViewModel.targetLanguage.uppercased()))")
                    .font(.headline)
                    .padding(.horizontal)
                    .padding(.top)

                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(recordingViewModel.translatedList.indices, id: \.self) { index in
                            Text(recordingViewModel.translatedList[index])
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.blue.opacity(0.1))
                                .cornerRadius(8)
                                .font(.system(size: 14, weight: .semibold))
                        }
                    }
                    .padding(.horizontal)
                }
            }

            Divider().padding(.top, 8)

            // Timer and Record/Pause/Resume button
            VStack(spacing: 16) {
                Text(recordingViewModel.timeLabel)
                    .font(.system(size: 40))

                Button(action: {
                    if recordingViewModel.isRecording {
                        recordingViewModel.isPaused
                            ? recordingViewModel.resumeRecording()
                            : recordingViewModel.pauseRecording()
                    } else {
                        recordingViewModel.startRecording()
                    }
                }) {
                    Image(systemName: recordingViewModel.isRecording
                          ? (recordingViewModel.isPaused ? "play.circle.fill" : "pause.circle.fill")
                          : "mic.circle.fill")
                        .font(.system(size: 50))
                        .foregroundStyle(.black)
                }
            }
            .padding(.bottom, 40)
        }
        .padding(.top)
        .background(Color(.systemBackground))
        .overlay(
            Group {
                if recordingViewModel.isLoading {
                    ZStack {
                        Color.black.opacity(0.4).ignoresSafeArea()
                        ProgressView("Processing...")
                            .padding()
                            .background(Color.white)
                            .cornerRadius(10)
                    }
                }
            }
        )
        .sheet(isPresented: $showSubmitView) {
            FinalTranscriptView(
                transcription: recordingViewModel.finalScript,
                translation: recordingViewModel.finalTranslatedScript,
                onDismiss: {
                    showSubmitView = false
                    onDismiss()
                }
            )
        }
        .sheet(isPresented: $showPremiumSheet) {
            VStack(spacing: 16) {
                if let product = iapManager.products.first {
                    Text("Unlock Premium for \(product.displayPrice)")
                        .font(.headline)
                    Button("Purchase") {
                        iapManager.purchasePremium()
                        showPremiumSheet = false
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Restore Purchases") {
                        Task {
                            await iapManager.restorePurchases()
                        }
                        showPremiumSheet = false
                    }
                    .buttonStyle(.bordered)
                } else {
                    ProgressView("Loading...")
                }
                Button("Cancel") {
                    showPremiumSheet = false
                }
            }
            .padding()
        }
    }
}

#Preview("Recording View") {
    RecordingView {
        // Dummy dismiss handler
        print("RecordingView dismissed")
    }
}

/// Simple view to show final transcript and translation.
struct FinalTranscriptView: View {
    let transcription: String
    let translation: String
    let onDismiss: () -> Void
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Final Transcription")
                        .font(.headline)
                    Text(transcription)
                        .padding()
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(8)
                    
                    if !translation.isEmpty {
                        Text("Translation")
                            .font(.headline)
                        Text(translation)
                            .padding()
                            .background(Color.blue.opacity(0.1))
                            .cornerRadius(8)
                    }
                }
                .padding()
            }
            .navigationTitle("Session Complete")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        onDismiss()
                    }
                }
            }
        }
    }
}

#Preview("Final Transcript") {
    FinalTranscriptView(
        transcription: "Sample transcription text here.",
        translation: "Sample translation text here.",
        onDismiss: {}
    )
}
