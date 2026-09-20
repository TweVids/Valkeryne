import { AppSettings, LiveModelId } from '../types';
import { audioPlayerService } from './audioPlayer';

export interface GeminiLiveCallbacks {
  onAiMessageStart: (messageId: string) => void;
  onTextUpdate: (messageId: string, text: string) => void;
  onReasoningUpdate: (messageId: string, reasoning: string) => void;
  onAudioChunkReceived: (messageId: string, pcmChunkBase64: string) => void;
  onComplete: (messageId: string) => void;
  onError: (messageId: string, errorMessage: string) => void;
  onConnectionStatusChange: (status: 'disconnected' | 'connecting' | 'connected') => void;
}

export class GeminiLiveService {
  private ws: WebSocket | null = null;
  private currentMessageId: string | null = null;
  private callbacks: GeminiLiveCallbacks;
  private settings: AppSettings;
  private accumulatedText: string = '';
  private accumulatedReasoning: string = '';
  private isConnecting: boolean = false;
  private setupSent: boolean = false;

  constructor(settings: AppSettings, callbacks: GeminiLiveCallbacks) {
    this.settings = settings;
    this.callbacks = callbacks;
  }

  public updateSettings(newSettings: AppSettings) {
    const modelChanged = this.settings.modelId !== newSettings.modelId;
    const keyChanged = this.settings.apiKey !== newSettings.apiKey;
    this.settings = newSettings;

    // If active connection has changed model or key, disconnect to re-establish on next message
    if (this.ws && (modelChanged || keyChanged)) {
      this.disconnect();
    }
  }

  public isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN && this.setupSent;
  }

  public async connect(): Promise<void> {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    if (!this.settings.apiKey || this.settings.apiKey.trim() === '') {
      throw new Error('API Key is missing. Please set your Gemini API key in Settings.');
    }

    this.isConnecting = true;
    this.callbacks.onConnectionStatusChange('connecting');

    return new Promise((resolve, reject) => {
      try {
        const url = `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${this.settings.apiKey.trim()}`;
        const ws = new WebSocket(url);
        this.ws = ws;

        ws.onopen = () => {
          this.isConnecting = false;
          this.sendSetup();
          this.callbacks.onConnectionStatusChange('connected');
          resolve();
        };

        ws.onmessage = (event) => {
          this.handleMessage(event.data);
        };

        ws.onerror = (error: any) => {
          this.isConnecting = false;
          const msg = error?.message || 'WebSocket error encountered';
          if (this.currentMessageId) {
            this.callbacks.onError(this.currentMessageId, `Connection Error: ${msg}`);
          }
          this.callbacks.onConnectionStatusChange('disconnected');
          reject(new Error(msg));
        };

        ws.onclose = (event) => {
          this.isConnecting = false;
          this.setupSent = false;
          this.ws = null;
          this.callbacks.onConnectionStatusChange('disconnected');

          if (event.code !== 1000 && this.currentMessageId) {
            const reason = event.reason || `Closed with code ${event.code}`;
            this.callbacks.onError(this.currentMessageId, `Connection closed unexpectedly: ${reason}`);
          }
        };
      } catch (err: any) {
        this.isConnecting = false;
        this.callbacks.onConnectionStatusChange('disconnected');
        reject(err);
      }
    });
  }

  private sendSetup() {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const setupPayload = {
      setup: {
        model: `models/${this.settings.modelId}`,
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: this.settings.voiceName || 'Puck',
              },
            },
          },
        },
        systemInstruction: {
          parts: [{ text: this.settings.systemInstruction || 'You are a helpful voice AI.' }],
        },
        outputAudioTranscription: {},
        inputAudioTranscription: {},
      },
    };

    this.ws.send(JSON.stringify(setupPayload));
    this.setupSent = true;
  }

  public async sendMessage(prompt: string, messageId: string): Promise<void> {
    this.currentMessageId = messageId;
    this.accumulatedText = '';
    this.accumulatedReasoning = '';

    this.callbacks.onAiMessageStart(messageId);

    try {
      if (!this.isConnected()) {
        await this.connect();
      }

      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        throw new Error('Could not establish connection to Gemini Live.');
      }

      // Send realtime text input
      const textMessage = {
        realtimeInput: {
          text: prompt,
        },
      };

      this.ws.send(JSON.stringify(textMessage));
    } catch (err: any) {
      this.callbacks.onError(messageId, err?.message || 'Failed to send message.');
    }
  }

  private handleMessage(rawData: any) {
    if (!this.currentMessageId) return;

    try {
      let data: any;
      if (typeof rawData === 'string') {
        data = JSON.parse(rawData);
      } else {
        return;
      }

      // Handle top-level API error
      if (data.error) {
        const errorDesc = data.error.message || JSON.stringify(data.error);
        this.callbacks.onError(this.currentMessageId, `Gemini API Error: ${errorDesc}`);
        return;
      }

      const serverContent = data.serverContent;
      if (!serverContent) return;

      // Handle model turn parts (Audio, Reasoning, Direct text)
      if (serverContent.modelTurn && Array.isArray(serverContent.modelTurn.parts)) {
        for (const part of serverContent.modelTurn.parts) {
          // Check for reasoning / thought
          if (part.thought === true || (part.text && part.thought)) {
            this.accumulatedReasoning += part.text;
            this.callbacks.onReasoningUpdate(this.currentMessageId, this.accumulatedReasoning);
          } else if (part.text) {
            this.accumulatedText += part.text;
            this.callbacks.onTextUpdate(this.currentMessageId, this.accumulatedText);
          }

          // Check for audio PCM data
          if (part.inlineData && part.inlineData.data) {
            const audioData = part.inlineData.data;
            this.callbacks.onAudioChunkReceived(this.currentMessageId, audioData);
            // Immediately play sequential streaming audio chunk
            audioPlayerService.enqueuePcmChunk(audioData, this.currentMessageId);
          }
        }
      }

      // Handle transcription if received and direct text wasn't provided
      if (serverContent.outputTranscription && serverContent.outputTranscription.text) {
        const transcript = serverContent.outputTranscription.text;
        if (!this.accumulatedText.includes(transcript)) {
          this.accumulatedText += (this.accumulatedText.length > 0 ? ' ' : '') + transcript;
          this.callbacks.onTextUpdate(this.currentMessageId, this.accumulatedText);
        }
      }

      // Check if turn complete
      if (serverContent.turnComplete === true) {
        this.callbacks.onComplete(this.currentMessageId);
      }
    } catch (err: any) {
      console.error('Failed to parse Gemini Live message', err);
      if (this.currentMessageId) {
        this.callbacks.onError(this.currentMessageId, `Parse error: ${err?.message || 'Invalid JSON'}`);
      }
    }
  }

  public disconnect() {
    if (this.ws) {
      try {
        this.ws.close(1000, 'Normal closure');
      } catch {}
      this.ws = null;
    }
    this.setupSent = false;
    this.currentMessageId = null;
    this.callbacks.onConnectionStatusChange('disconnected');
  }
}
