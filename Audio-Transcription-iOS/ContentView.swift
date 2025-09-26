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
    @State private var showSubmitView = false

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

            VStack(spacing: 28) {
                languageSelectionButton

                VStack(spacing: 12) {
                    Text(recordingViewModel.timeLabel)
                        .font(.system(size: 40, weight: .medium))
                        .monospacedDigit()

                    Text("Translating to \(recordingViewModel.selectedLanguageName)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                Button(action: handlePrimaryButtonTap) {
                    Text(recordingViewModel.isRecording ? "Stop" : "Start")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 56)
                        .padding(.vertical, 20)
                        .background(recordingViewModel.isRecording ? Color.red : Color.blue)
                        .cornerRadius(18)
                        .shadow(color: Color.black.opacity(0.15), radius: 10, x: 0, y: 6)
                }
                .accessibilityIdentifier("primaryRecordingButton")
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)

            ScrollView {
                VStack(spacing: 20) {
                    TranscriptCardView(
                        title: "Live Transcript",
                        subtitle: "Real-time transcription",
                        text: recordingViewModel.transcriptionText,
                        placeholder: "Your words will appear here as you speak.",
                        accent: Color.gray.opacity(0.2)
                    )

                    TranscriptCardView(
                        title: "Translated Text",
                        subtitle: recordingViewModel.selectedLanguageName,
                        text: recordingViewModel.translationText,
                        placeholder: "We are listening for speech to translate…",
                        accent: Color.blue.opacity(0.18)
                    )
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 32)
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .overlay(
            Group {
                if recordingViewModel.isLoading {
                    ZStack {
                        Color.black.opacity(0.3).ignoresSafeArea()
                        LoadingProgressView(progress: $recordingViewModel.loadingProgress, stages: recordingViewModel.loadingStages)
                            .padding()
                            .background(Color(.systemBackground))
                            .cornerRadius(12)
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
        .sheet(isPresented: $recordingViewModel.isLanguageSheetVisible) {
            LanguageSelectionView(
                selection: $recordingViewModel.translationSelection,
                options: recordingViewModel.availableLanguages
            )
        }
    }

    private var languageSelectionButton: some View {
        Button {
            recordingViewModel.isLanguageSheetVisible = true
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Desired Language")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Text(recordingViewModel.selectedLanguageName)
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundColor(.primary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.headline)
                    .foregroundColor(.secondary)
            }
            .padding()
            .frame(maxWidth: .infinity)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(18)
        }
        .buttonStyle(.plain)
    }

    private func handlePrimaryButtonTap() {
        if recordingViewModel.isRecording {
            recordingViewModel.stopRecording()
            recordingViewModel.finalizeTranscription()
            showSubmitView = true
        } else {
            recordingViewModel.startRecording()
        }
    }
}

private struct TranscriptCardView: View {
    let title: String
    let subtitle: String
    let text: String
    let placeholder: String
    let accent: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(.primary)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Spacer()
            }

            if text.isEmpty {
                Text(placeholder)
                    .font(.body)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(accent.opacity(0.35))
                    .cornerRadius(14)
            } else {
                Text(text)
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.primary)
                    .lineSpacing(6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(accent)
                    .cornerRadius(14)
            }
        }
    }
}

private struct LanguageSelectionView: View {
    @Binding var selection: String
    let options: [LanguageOption]
    @Environment(\.dismiss) private var dismiss
    @State private var searchText: String = ""

    private var filteredOptions: [LanguageOption] {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return options }
        return options.filter { option in
            option.name.lowercased().contains(trimmed.lowercased()) ||
            option.code.lowercased().contains(trimmed.lowercased())
        }
    }

    var body: some View {
        NavigationView {
            List {
                ForEach(filteredOptions) { option in
                    Button {
                        selection = option.code
                        dismiss()
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(option.name)
                                    .foregroundColor(.primary)
                                Text(option.code.uppercased())
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            if option.code == selection {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.blue)
                            }
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Choose Language")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
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

private struct LoadingProgressView: View {
    @Binding var progress: Double
    let stages: [String]

    var body: some View {
        VStack(spacing: 16) {
            ProgressView(value: progress)
                .progressViewStyle(LinearProgressViewStyle(tint: .blue))
            
            let currentStageIndex = Int(progress * Double(stages.count - 1))
            let currentStage = stages[min(currentStageIndex, stages.count - 1)]
            let percentage = Int(progress * 100)
            
            Text("\(currentStage) (\(percentage)%)")
                .font(.headline)
                .foregroundColor(.primary)
            
            Text("Please wait while connecting...")
                .font(.subheadline)
                .foregroundColor(.secondary)
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
