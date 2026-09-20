# Valkeryne

Valkeryne is a high-performance, native Android voice and multimodal AI application powered by the **Google Gemini Live API** (WebSockets).

Built with **Kotlin** and **Jetpack Compose (Material 3)**.

## Key Features

- **100% Native Android**:
  - Zero Metro bundler or development server requirements.
  - Zero JavaScript bridging overhead.
  - Installs and runs as a completely standalone Android application.
- **Native Audio Streaming (`AudioTrack`)**:
  - Plays raw 16-bit PCM (24 kHz mono) streaming audio natively.
  - Sequential playback ($1$ to $N$) begins **immediately upon receiving chunk 1** with zero delay.
- **Gemini Live API Integration**:
  - Real-time bidirectional communication over WebSockets using OkHttp.
- **Strictly `-live-` Model Selection**:
  - `gemini-3.8-live`: Default ultra-low latency voice agent.
  - `gemini-3.8-live-extended-thinking`: High background reasoning with thought turns.
  - `gemini-3.1-flash-live-preview`: Low-latency conversational dialogue preview.
  - `gemini-2.5-flash-native-audio-preview-12-2025`: Native audio streaming model.
- **Audio Replay Button**:
  - Positioned directly beneath AI responses (or standalone when model returns audio only).
  - Allows listening / replaying the full spoken response.
- **Reasoning Process**:
  - Differentiates and displays the model's internal thought process in an expandable card.
- **Inline Error Handling**:
  - Displays any WebSocket or Gemini API errors directly inside the response bubble.
- **Settings**:
  - Top-left button to configure your Gemini API Key, select the model, choose voice persona (Puck, Charon, Kore, Fenrir, Aoede), and customize system instructions.
- **UI Design**:
  - Gray background theme (`#E5E7EB`).
  - Large blank conversation area displaying user messages on the right and AI responses on the left.
  - Small square input box with rounded corners (`8dp`) next to a send button.

## Automated Cloud Builds (GitHub Actions)

This repository automatically compiles and packages the APK using GitHub Actions on every push:
1. Builds native debug APK (`app-debug.apk`).
2. Uploads the standalone `.apk` directly to GitHub Actions artifacts for instant download and installation on any Android phone.
