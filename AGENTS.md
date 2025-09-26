# Repository Guidelines

## Project Objective

WhisperLive is a real-time speech-to-text system based on OpenAI's Whisper, featuring a client-server architecture for low-latency transcription over WebSockets. It supports backends like faster_whisper, TensorRT, and OpenVINO, with VAD and multi-platform clients (iOS, Android, desktop, browser). Objective: Integrate Meta's SeamlessM4T v2 Large (ONNX-exported) for optional real-time translation of transcribed speech to any user-selected language.

## Project Structure & Module Organization

- `whisper_live/` contains the Python package: `server.py`/`client.py` handle streaming, `transcriber/` implements Faster-Whisper, TensorRT, and OpenVINO backends, and `vad.py` supplies voice-activity detection.
- `tests/` houses unittest suites for client streaming, server management, and VAD helpers; they rely on fixtures in `assets/` such as `assets/jfk.flac`.
- `scripts/` holds setup and TensorRT helpers; `docker/` and `docs/` track deployment artifacts; platform clients live under `Audio-Transcription-*` and `Desktop-Client-Windows/`.

## Build, Test, and Development Commands

- `python -m venv .venv && source .venv/bin/activate` creates an isolated environment.
- `pip install -e .` plus `pip install -r requirements/server.txt` (or `client.txt`) prepares local development; run `bash scripts/setup.sh` if PyAudio headers are missing.
- `python run_server.py --backend faster_whisper --port 9090 --max_clients 4` starts a CPU-friendly server; add `--omp_num_threads 4` to tune CPU usage or `--backend tensorrt` with `--trt <engine_dir>` for GPU.
- `python run_client.py --files assets/jfk.flac --model small --translate` streams sample audio against a running server.

## Coding Style & Naming Conventions

- Follow idiomatic Python 3.9+: four-space indentation, snake_case modules and functions, CapWords classes, and descriptive docstrings similar to `ClientManager` in `whisper_live/server.py`.
- Use type hints where practical and keep logging consistent with existing `logging` usage; prefer explicit resource cleanup (e.g., context managers) over implicit closures.
- No formatter is enforced, but align with PEP 8 and keep imports grouped (stdlib, third-party, local).

## Testing Guidelines

- Run `pytest tests` after activating the virtualenv; expect integration tests to spawn `run_server.py` and write artifacts in the repo root.
- For quick checks, target modules with `pytest tests/test_client.py -k WebSocket`; remove generated `.srt` files when adding new cases.
- Maintain test data under `assets/` and prefer deterministic audio samples with documented expected output.
- After code change, always test what was built works and fix if issues occur.

## Commit & Pull Request Guidelines

- Match the Git history: start the subject with a concise, present-tense summary (“Add real-time STT for iOS clients”), and elaborate in the body if multiple domains are touched.
- Reference issues or design docs, list manual/automated tests run, and attach screenshots or logs for UI or latency-impacting changes.
- PRs should describe backend selection (faster_whisper, TensorRT, OpenVINO) implications and note any new dependencies or environment variables.

## Security & Configuration Tips

- Do not commit model weights, API keys, or customer audio; rely on `.gitignore` for cached models (`~/.cache/whisper-live/`).
- Document required environment tweaks (e.g., `OMP_NUM_THREADS`, CUDA paths) within your changes to aid operators and downstream agents.

## Agent Best Practices

- Follow best practices in all code changes and implementations.
- Consult the latest documents from Context7 for up-to-date library and API information.
- Use the latest versions and syntax at all times if possible, ensuring compatibility.
- Include correct, descriptive error messages and comprehensive logging for all potential failure points to facilitate debugging and error diagnosis.
- Always test after adding new code. If issues occur, always fix them or ask for help/direction if needed.
