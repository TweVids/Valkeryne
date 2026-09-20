import { Platform } from 'react-native';
import { createAudioPlayer, AudioPlayer, AudioStatus } from 'expo-audio';
import {
  pcmChunkToWavBase64,
  combinePcmChunksToWavBytes,
  saveWavBytesToCache,
  base64ToUint8Array,
  createWavFromPcm,
} from '../utils/wavHelper';

class AudioPlayerService {
  private currentPlayer: AudioPlayer | null = null;
  private currentWebAudio: any | null = null;
  private isPlayingState: boolean = false;
  private streamQueue: string[] = []; // URIs to play in sequence
  private isProcessingQueue: boolean = false;
  private onStateChangeListeners: Array<(isPlaying: boolean, activeMessageId?: string) => void> = [];
  private currentActiveMessageId?: string;

  public addListener(listener: (isPlaying: boolean, activeMessageId?: string) => void) {
    this.onStateChangeListeners.push(listener);
    return () => {
      this.onStateChangeListeners = this.onStateChangeListeners.filter((l) => l !== listener);
    };
  }

  private notifyState(isPlaying: boolean, messageId?: string) {
    this.isPlayingState = isPlaying;
    this.currentActiveMessageId = messageId;
    for (const listener of this.onStateChangeListeners) {
      listener(isPlaying, messageId);
    }
  }

  public isPlaying(): boolean {
    return this.isPlayingState;
  }

  public getActiveMessageId(): string | undefined {
    return this.currentActiveMessageId;
  }

  /**
   * Stops any ongoing playback and empties the streaming queue
   */
  public async stop(): Promise<void> {
    this.streamQueue = [];
    this.isProcessingQueue = false;

    if (this.currentPlayer) {
      try {
        this.currentPlayer.pause();
        this.currentPlayer.release();
      } catch (err) {
        console.warn('Error releasing native audio player', err);
      }
      this.currentPlayer = null;
    }

    if (this.currentWebAudio) {
      try {
        this.currentWebAudio.pause();
        this.currentWebAudio = null;
      } catch (err) {
        console.warn('Error pausing web audio', err);
      }
    }

    this.notifyState(false, undefined);
  }

  /**
   * Enqueues a raw PCM audio chunk (base64) received from the Live API.
   * Plays package 1 as soon as it arrives, followed sequentially by 2..N.
   */
  public async enqueuePcmChunk(pcmBase64: string, messageId: string): Promise<void> {
    try {
      let audioUri: string;

      if (Platform.OS === 'web') {
        const pcmBytes = base64ToUint8Array(pcmBase64);
        const wavBytes = createWavFromPcm(pcmBytes, 24000);
        // On web, convert bytes to blob URL
        const blob = new Blob([wavBytes.buffer as ArrayBuffer], { type: 'audio/wav' });
        audioUri = URL.createObjectURL(blob);
      } else {
        const pcmBytes = base64ToUint8Array(pcmBase64);
        const wavBytes = createWavFromPcm(pcmBytes, 24000);
        audioUri = await saveWavBytesToCache(wavBytes, `chunk_${messageId}`);
      }

      this.streamQueue.push(audioUri);
      this.currentActiveMessageId = messageId;

      if (!this.isProcessingQueue) {
        this.processQueue(messageId);
      }
    } catch (err) {
      console.error('Failed to enqueue PCM chunk', err);
    }
  }

  private async processQueue(messageId: string): Promise<void> {
    if (this.streamQueue.length === 0) {
      this.isProcessingQueue = false;
      this.notifyState(false, undefined);
      return;
    }

    this.isProcessingQueue = true;
    this.notifyState(true, messageId);

    const nextUri = this.streamQueue.shift();
    if (!nextUri) {
      this.isProcessingQueue = false;
      this.notifyState(false, undefined);
      return;
    }

    await this.playUri(nextUri, messageId, () => {
      // Once this chunk finishes, process the next in queue
      this.processQueue(messageId);
    });
  }

  /**
   * Replay all chunks for a specific message by combining them into one complete WAV
   */
  public async playFullMessageAudio(
    pcmChunks: string[],
    messageId: string
  ): Promise<void> {
    if (this.isPlayingState && this.currentActiveMessageId === messageId) {
      // Toggle off if currently playing this message
      await this.stop();
      return;
    }

    await this.stop();

    if (!pcmChunks || pcmChunks.length === 0) return;

    try {
      this.notifyState(true, messageId);
      let audioUri: string;

      if (Platform.OS === 'web') {
        const wavBytes = combinePcmChunksToWavBytes(pcmChunks, 24000);
        const blob = new Blob([wavBytes.buffer as ArrayBuffer], { type: 'audio/wav' });
        audioUri = URL.createObjectURL(blob);
      } else {
        const wavBytes = combinePcmChunksToWavBytes(pcmChunks, 24000);
        audioUri = await saveWavBytesToCache(wavBytes, `full_${messageId}`);
      }

      await this.playUri(audioUri, messageId, () => {
        this.notifyState(false, undefined);
      });
    } catch (err) {
      console.error('Failed to play full audio', err);
      this.notifyState(false, undefined);
    }
  }

  private playUri(uri: string, messageId: string, onEnded: () => void): Promise<void> {
    return new Promise((resolve) => {
      if (Platform.OS === 'web') {
        try {
          const audio = new Audio(uri);
          this.currentWebAudio = audio;
          audio.onended = () => {
            onEnded();
            resolve();
          };
          audio.onerror = (e) => {
            console.warn('Web audio playback error', e);
            onEnded();
            resolve();
          };
          audio.play().catch((err) => {
            console.warn('Web audio play promise rejected', err);
            onEnded();
            resolve();
          });
        } catch (e) {
          console.warn('Web audio init error', e);
          onEnded();
          resolve();
        }
      } else {
        try {
          let hasFinished = false;
          const finishOnce = () => {
            if (!hasFinished) {
              hasFinished = true;
              if (this.currentPlayer) {
                try {
                  this.currentPlayer.release();
                } catch {}
                this.currentPlayer = null;
              }
              onEnded();
              resolve();
            }
          };

          const player = createAudioPlayer({ uri });
          this.currentPlayer = player;

          player.addListener('playbackStatusUpdate', (status: AudioStatus) => {
            if (
              status.playbackState === 'ended' ||
              (!status.playing && status.currentTime >= (status.duration || 0) && (status.duration || 0) > 0)
            ) {
              finishOnce();
            }
          });

          player.play();

          // Safety timeout in case playbackStatusUpdate event is missed
          setTimeout(() => {
            if (this.currentPlayer === player && !hasFinished) {
              // Estimate duration based on file or default fallback
              // player status check
              if (!player.playing) {
                finishOnce();
              }
            }
          }, 30000);
        } catch (e) {
          console.error('Failed to create or play native AudioPlayer', e);
          onEnded();
          resolve();
        }
      }
    });
  }
}

export const audioPlayerService = new AudioPlayerService();
