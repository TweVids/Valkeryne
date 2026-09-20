import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  View,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  SafeAreaView,
  Text,
  Alert,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { AppSettings, ChatMessageItem } from './src/types';
import { DEFAULT_SETTINGS, loadSettings, saveSettings } from './src/services/storage';
import { GeminiLiveService } from './src/services/geminiLiveService';
import { audioPlayerService } from './src/services/audioPlayer';
import { Header } from './src/components/Header';
import { ChatMessage } from './src/components/ChatMessage';
import { ChatInput } from './src/components/ChatInput';
import { SettingsModal } from './src/components/SettingsModal';

export default function App() {
  const [settings, setSettings] = useState<AppSettings>(DEFAULT_SETTINGS);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState<
    'disconnected' | 'connecting' | 'connected'
  >('disconnected');
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [activePlayingMessageId, setActivePlayingMessageId] = useState<string | undefined>();
  const [isSending, setIsSending] = useState(false);

  const flatListRef = useRef<FlatList>(null);
  const liveServiceRef = useRef<GeminiLiveService | null>(null);

  // Load saved settings on startup
  useEffect(() => {
    loadSettings().then((loaded) => {
      setSettings(loaded);
      // If no API key is configured on first launch, open settings
      if (!loaded.apiKey) {
        setIsSettingsOpen(true);
      }
    });
  }, []);

  // Listen for audio player state updates (for UI audio button sync)
  useEffect(() => {
    const unsubscribe = audioPlayerService.addListener((isPlaying, messageId) => {
      setActivePlayingMessageId(isPlaying ? messageId : undefined);
    });
    return () => {
      unsubscribe();
    };
  }, []);

  // Initialize or update Gemini Live Service whenever settings change
  useEffect(() => {
    if (liveServiceRef.current) {
      liveServiceRef.current.updateSettings(settings);
    } else {
      liveServiceRef.current = new GeminiLiveService(settings, {
        onAiMessageStart: (aiMsgId) => {
          setIsSending(false);
          setMessages((prev) => [
            ...prev,
            {
              id: aiMsgId,
              sender: 'ai',
              text: '',
              reasoning: '',
              audioChunks: [],
              isStreaming: true,
              timestamp: Date.now(),
            },
          ]);
        },
        onTextUpdate: (aiMsgId, newText) => {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMsgId ? { ...msg, text: newText } : msg
            )
          );
        },
        onReasoningUpdate: (aiMsgId, reasoning) => {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMsgId ? { ...msg, reasoning } : msg
            )
          );
        },
        onAudioChunkReceived: (aiMsgId, pcmChunk) => {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMsgId
                ? { ...msg, audioChunks: [...msg.audioChunks, pcmChunk] }
                : msg
            )
          );
        },
        onComplete: (aiMsgId) => {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMsgId ? { ...msg, isStreaming: false } : msg
            )
          );
        },
        onError: (aiMsgId, errorMessage) => {
          setIsSending(false);
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMsgId
                ? { ...msg, error: errorMessage, isStreaming: false }
                : msg
            )
          );
        },
        onConnectionStatusChange: (status) => {
          setConnectionStatus(status);
        },
      });
    }
  }, [settings]);

  const handleSaveSettings = async (newSettings: AppSettings) => {
    setSettings(newSettings);
    await saveSettings(newSettings);
  };

  const handleSendMessage = async (text: string) => {
    if (!settings.apiKey || settings.apiKey.trim() === '') {
      setIsSettingsOpen(true);
      Alert.alert(
        'API Key Required',
        'Please enter your Gemini API key in Settings to connect to Gemini Live.'
      );
      return;
    }

    // Stop any existing audio playback
    await audioPlayerService.stop();

    const userMessageId = `user_${Date.now()}`;
    const aiMessageId = `ai_${Date.now()}`;

    // Add user message
    setMessages((prev) => [
      ...prev,
      {
        id: userMessageId,
        sender: 'user',
        text,
        audioChunks: [],
        timestamp: Date.now(),
      },
    ]);

    setIsSending(true);

    if (liveServiceRef.current) {
      await liveServiceRef.current.sendMessage(text, aiMessageId);
    }
  };

  const handleTogglePlayAudio = async (message: ChatMessageItem) => {
    if (activePlayingMessageId === message.id) {
      await audioPlayerService.stop();
    } else {
      await audioPlayerService.playFullMessageAudio(message.audioChunks, message.id);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar style="dark" />

      {/* Header with Settings Button on top left */}
      <Header
        settings={settings}
        connectionStatus={connectionStatus}
        onOpenSettings={() => setIsSettingsOpen(true)}
      />

      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.container}
      >
        {/* Large area of blank displaying chat messages */}
        <View style={styles.chatArea}>
          {messages.length === 0 ? (
            <View style={styles.emptyStateContainer}>
              <Text style={styles.emptyStateTitle}>Valkeryne Live AI</Text>
              <Text style={styles.emptyStateSubtitle}>
                Real-time voice and reasoning chat using Gemini Live models.
              </Text>
              <Text style={styles.emptyStateHint}>
                {settings.apiKey
                  ? `Connected to ${settings.modelId}. Send a message below to start talking!`
                  : 'Tap the Settings icon at top-left to configure your API key.'}
              </Text>
            </View>
          ) : (
            <FlatList
              ref={flatListRef}
              data={messages}
              keyExtractor={(item) => item.id}
              renderItem={({ item }) => (
                <ChatMessage
                  message={item}
                  isPlaying={activePlayingMessageId === item.id}
                  onTogglePlayAudio={handleTogglePlayAudio}
                />
              )}
              contentContainerStyle={styles.listContent}
              onContentSizeChange={() =>
                flatListRef.current?.scrollToEnd({ animated: true })
              }
            />
          )}
        </View>

        {/* Small square box with slightly rounded corners next to send icon */}
        <ChatInput onSendMessage={handleSendMessage} disabled={isSending} />
      </KeyboardAvoidingView>

      {/* Settings Modal */}
      <SettingsModal
        visible={isSettingsOpen}
        settings={settings}
        onClose={() => setIsSettingsOpen(false)}
        onSave={handleSaveSettings}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#E5E7EB', // Neutral gray background filling the app
  },
  container: {
    flex: 1,
    backgroundColor: '#E5E7EB', // Gray background
  },
  chatArea: {
    flex: 1,
    backgroundColor: '#E5E7EB', // Gray background for large blank area
  },
  listContent: {
    paddingVertical: 12,
  },
  emptyStateContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  emptyStateTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: '#1F2937',
    marginBottom: 8,
  },
  emptyStateSubtitle: {
    fontSize: 14,
    color: '#4B5563',
    textAlign: 'center',
    marginBottom: 16,
    lineHeight: 20,
  },
  emptyStateHint: {
    fontSize: 12,
    color: '#6B7280',
    textAlign: 'center',
    backgroundColor: '#D1D5DB',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 16,
  },
});
