# WhisperLive Repository Architecture Analysis

## Overview

The WhisperLive repository implements a client-server architecture for real-time speech-to-text (STT) transcription over WebSockets, with support for multiple inference backends, voice activity detection (VAD), and optional translation. It processes streaming audio from various sources (microphone, files, RTSP/HLS streams) across platforms (Android, iOS, browser extensions, desktop Electron app).

### Key Components

1. **Client-Server Communication** (`client.py`, `server.py`):

   - Clients capture/stream audio in 4096-sample chunks (~0.256s at 16kHz) via WebSocket. Supports multi-client teeing and SRT export.
   - Server manages 4 concurrent clients, applies VAD, buffers speech, and transcribes via backends. Uses threading for translation.

2. **Voice Activity Detection** (`vad.py`):

   - Silero VAD (ONNX, 2024) on 32ms frames; threshold 0.5. Skips non-speech; resets after 3 silent chunks (~0.768s).

3. **Transcription** (`transcriber_faster_whisper.py`, etc.):

   - Primary: faster_whisper (CTranslate2 Whisper). 30s windows, VAD filtering, beam search/sampling, word timestamps.
   - Backends: TensorRT (GPU), OpenVINO (CPU). Buffers via VAD; no diarization.

4. **Translation** (`translation_backend.py`):

   - Post-transcription via SeamlessM4T v2 Large (ONNX/PyTorch). Queues segments; supports 100+ languages.

5. **Structure**:
   - Core: `whisper_live/` (Python package).
   - Clients: Platform-specific (Android/iOS/etc.).
   - Tools: Tests, Docker (CPU/GPU/etc.), scripts (e.g., ONNX export).

## Comparison to WhisperLiveKit

WhisperLiveKit emphasizes SOTA simultaneous speech (ultra-low latency via AlignAtt/LocalAgreement policies, diarization, NLLB translation).

### Worse Aspects

- **Latency/Streaming**: Simple VAD buffering (~1-2s delay); chunked processing risks mid-word cuts/context loss. Lacks SimulStreaming/WhisperStreaming for incremental decoding (<300ms).
- **Diarization**: Absent; single-speaker only. No Streaming Sortformer/Diart.
- **Translation**: Sequential (~500ms+ delay); not simultaneous.
- **Buffering**: Fixed chunks; no intelligent policies.

### Better Aspects

- **Deployment**: Multi-platform clients, Docker/backends (TensorRT/OpenVINO), tests/scripts. Production-ready.
- **Efficiency**: CTranslate2/ONNX for 5-10x speed; flexible hardware support.
- **Simplicity**: ~500-800 LOC core; easy to extend. Lighter than full SOTA stack.

### Verdict

Worse for ultra-low-latency/multi-speaker (add policies/diarization). Better for cross-platform/single-speaker deployment. To align: Integrate streaming policies and diarization.
