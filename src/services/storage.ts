import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppSettings, LiveModelId } from '../types';
import { DEFAULT_SYSTEM_INSTRUCTION } from '../constants/models';

const STORAGE_KEY = '@valkeryne_settings_v1';

export const DEFAULT_SETTINGS: AppSettings = {
  apiKey: '',
  modelId: 'gemini-3.8-live',
  voiceName: 'Puck',
  systemInstruction: DEFAULT_SYSTEM_INSTRUCTION,
};

export async function loadSettings(): Promise<AppSettings> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw);
    return {
      apiKey: parsed.apiKey || '',
      modelId: (parsed.modelId as LiveModelId) || 'gemini-3.8-live',
      voiceName: parsed.voiceName || 'Puck',
      systemInstruction: parsed.systemInstruction || DEFAULT_SYSTEM_INSTRUCTION,
    };
  } catch (err) {
    console.error('Failed to load settings from storage', err);
    return DEFAULT_SETTINGS;
  }
}

export async function saveSettings(settings: AppSettings): Promise<void> {
  try {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch (err) {
    console.error('Failed to save settings to storage', err);
  }
}
