export type LiveModelId =
  | 'gemini-3.8-live'
  | 'gemini-3.8-live-extended-thinking'
  | 'gemini-3.1-flash-live-preview'
  | 'gemini-2.5-flash-native-audio-preview-12-2025';

export interface ModelOption {
  id: LiveModelId;
  name: string;
  description: string;
  supportsExtendedThinking?: boolean;
}

export interface AppSettings {
  apiKey: string;
  modelId: LiveModelId;
  voiceName: string;
  systemInstruction: string;
}

export interface ChatMessageItem {
  id: string;
  sender: 'user' | 'ai';
  text?: string;
  reasoning?: string;
  audioChunks: string[]; // Base64 PCM chunks
  audioWavUri?: string; // Path or data URI to combined WAV
  isStreaming?: boolean;
  isPlayingAudio?: boolean;
  error?: string;
  timestamp: number;
}
