# Valkeryne

Valkeryne is a real-time voice and multimodal AI mobile application powered by the **Google Gemini Live API** (WebSockets).

## Features

- **Gemini Live API Integration**: Bidirectional streaming over WebSockets using `BidiGenerateContentSetup` and `realtimeInput`.
- **Strictly `-live-` Models Supported**:
  - `gemini-3.8-live`: Default ultra-low latency conversational voice agent.
  - `gemini-3.8-live-extended-thinking`: High background reasoning and thinking turns during live interactions.
  - `gemini-3.1-flash-live-preview`: Low-latency conversational dialogue preview.
  - `gemini-2.5-flash-native-audio-preview-12-2025`: Native audio streaming model.
- **Ordered Audio Streaming Playback**:
  - API returns multiple sequential audio packages (1..N).
  - Starts playing package 1 as soon as it is retrieved, continuing sequentially through 2..N without gaps.
- **Audio Replay Button**:
  - Displayed directly below each AI response (or as the standalone control if the model returns only audio).
  - Allows clicking to hear / replay the full audio response at any time.
- **Reasoning Process**:
  - Highlights the model's internal thought process / extended reasoning separate from the answer text.
- **Inline Error Handling**:
  - Displays any WebSocket or Gemini API errors directly inside the AI response bubble.
- **Customizable Settings**:
  - Top-left settings button to configure your Gemini API Key, select the `-live-` model, choose voice personas (Puck, Charon, Kore, Fenrir, Aoede), and set system instructions.
- **Mobile UI**:
  - Gray background theme.
  - Bottom input bar with a small square box (slightly rounded edges) and a send button.
  - Large scrollable chat history displaying user messages on the right and AI answers on the left.

## Automated CI/CD (GitHub Actions)

This repository includes a GitHub Actions workflow in `.github/workflows/build.yml` that automatically:
1. Validates and typechecks the TypeScript codebase.
2. Builds the web bundle.
3. Prebuilds and compiles a native **Android Debug APK** (`app-debug.apk`), uploaded directly as a build artifact for testing.

## Local Development

```bash
# Install dependencies
npm install

# Start development server
npm run start

# Run on Android emulator / device
npm run android

# Run on iOS simulator / device
npm run ios

# Run in web browser
npm run web
```
