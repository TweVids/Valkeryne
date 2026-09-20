import { ModelOption } from '../types';

export const LIVE_MODELS: ModelOption[] = [
  {
    id: 'gemini-3.8-live',
    name: 'Gemini 3.8 Live',
    description: 'Default ultra-low latency voice and multimodal interactions.',
  },
  {
    id: 'gemini-3.8-live-extended-thinking',
    name: 'Gemini 3.8 Live Extended Thinking',
    description: 'Real-time voice with high background reasoning and thought streaming.',
    supportsExtendedThinking: true,
  },
  {
    id: 'gemini-3.1-flash-live-preview',
    name: 'Gemini 3.1 Flash Live Preview',
    description: 'Low-latency live preview for conversational dialogue.',
  },
  {
    id: 'gemini-2.5-flash-native-audio-preview-12-2025',
    name: 'Gemini 2.5 Flash Native Audio Preview',
    description: 'Native audio model with streaming voice responses.',
  },
];

export const AVAILABLE_VOICES = [
  'Puck',
  'Charon',
  'Kore',
  'Fenrir',
  'Aoede',
];

export const DEFAULT_SYSTEM_INSTRUCTION =
  'You are a fast, concise, and helpful voice AI assistant. Keep responses natural and conversational.';
