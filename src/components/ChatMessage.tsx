import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { ChatMessageItem } from '../types';

interface ChatMessageProps {
  message: ChatMessageItem;
  isPlaying: boolean;
  onTogglePlayAudio: (message: ChatMessageItem) => void;
}

export const ChatMessage: React.FC<ChatMessageProps> = ({
  message,
  isPlaying,
  onTogglePlayAudio,
}) => {
  const isUser = message.sender === 'user';
  const [showReasoning, setShowReasoning] = useState(true);

  if (isUser) {
    return (
      <View style={styles.userRow}>
        <View style={styles.userBubble}>
          <Text style={styles.userText}>{message.text}</Text>
        </View>
      </View>
    );
  }

  // AI Message
  const hasAudio = message.audioChunks && message.audioChunks.length > 0;
  const hasText = message.text && message.text.trim().length > 0;
  const hasReasoning = message.reasoning && message.reasoning.trim().length > 0;
  const hasError = !!message.error;

  return (
    <View style={styles.aiRow}>
      <View style={styles.aiBubble}>
        {/* Error State */}
        {hasError && (
          <View style={styles.errorContainer}>
            <Ionicons name="alert-circle" size={18} color="#DC2626" />
            <Text style={styles.errorText}>{message.error}</Text>
          </View>
        )}

        {/* Reasoning / Thinking Section */}
        {hasReasoning && (
          <View style={styles.reasoningContainer}>
            <TouchableOpacity
              style={styles.reasoningHeader}
              onPress={() => setShowReasoning(!showReasoning)}
              activeOpacity={0.7}
            >
              <View style={styles.reasoningTitleRow}>
                <Ionicons name="bulb-outline" size={15} color="#8B5CF6" />
                <Text style={styles.reasoningTitle}>Reasoning Process</Text>
              </View>
              <Ionicons
                name={showReasoning ? 'chevron-up' : 'chevron-down'}
                size={16}
                color="#6B7280"
              />
            </TouchableOpacity>

            {showReasoning && (
              <Text style={styles.reasoningText}>{message.reasoning}</Text>
            )}
          </View>
        )}

        {/* AI Answer Text */}
        {hasText ? (
          <Text style={styles.aiText}>{message.text}</Text>
        ) : !hasError && !hasAudio && message.isStreaming ? (
          <View style={styles.loadingRow}>
            <ActivityIndicator size="small" color="#4B5563" />
            <Text style={styles.loadingText}>Generating response...</Text>
          </View>
        ) : null}

        {/* Audio Button below the response (or standalone if model returned only audio) */}
        {hasAudio && (
          <View style={styles.audioButtonContainer}>
            <TouchableOpacity
              style={[
                styles.audioButton,
                isPlaying && styles.audioButtonPlaying,
              ]}
              onPress={() => onTogglePlayAudio(message)}
              activeOpacity={0.8}
            >
              <Ionicons
                name={isPlaying ? 'pause-circle' : 'play-circle'}
                size={22}
                color={isPlaying ? '#FFFFFF' : '#1F2937'}
              />
              <Text
                style={[
                  styles.audioButtonText,
                  isPlaying && styles.audioButtonTextPlaying,
                ]}
              >
                {isPlaying
                  ? 'Playing Audio...'
                  : `Play Spoken Response (${message.audioChunks.length} chunks)`}
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Streaming indicator if still receiving audio chunks */}
        {message.isStreaming && hasAudio && (
          <View style={styles.streamingIndicatorRow}>
            <ActivityIndicator size="small" color="#2563EB" />
            <Text style={styles.streamingIndicatorText}>
              Streaming audio chunk #{message.audioChunks.length}...
            </Text>
          </View>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  userRow: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginVertical: 6,
    paddingHorizontal: 16,
  },
  userBubble: {
    maxWidth: '82%',
    backgroundColor: '#1F2937', // Dark sleek bubble
    borderRadius: 18,
    borderBottomRightRadius: 4,
    paddingHorizontal: 16,
    paddingVertical: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 1,
  },
  userText: {
    color: '#F9FAFB',
    fontSize: 15,
    lineHeight: 21,
  },
  aiRow: {
    flexDirection: 'row',
    justifyContent: 'flex-start',
    marginVertical: 6,
    paddingHorizontal: 16,
  },
  aiBubble: {
    maxWidth: '88%',
    backgroundColor: '#FFFFFF',
    borderRadius: 18,
    borderBottomLeftRadius: 4,
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  aiText: {
    color: '#111827',
    fontSize: 15,
    lineHeight: 22,
  },
  reasoningContainer: {
    backgroundColor: '#F5F3FF', // Soft purple
    borderWidth: 1,
    borderColor: '#DDD6FE',
    borderRadius: 10,
    padding: 10,
    marginBottom: 10,
  },
  reasoningHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  reasoningTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  reasoningTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: '#6D28D9',
  },
  reasoningText: {
    fontSize: 13,
    fontStyle: 'italic',
    color: '#4B5563',
    lineHeight: 18,
    marginTop: 6,
    paddingTop: 6,
    borderTopWidth: 1,
    borderTopColor: '#EDE9FE',
  },
  audioButtonContainer: {
    marginTop: 10,
    alignItems: 'flex-start',
  },
  audioButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F3F4F6',
    borderWidth: 1,
    borderColor: '#D1D5DB',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 12,
    gap: 8,
  },
  audioButtonPlaying: {
    backgroundColor: '#2563EB',
    borderColor: '#2563EB',
  },
  audioButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1F2937',
  },
  audioButtonTextPlaying: {
    color: '#FFFFFF',
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FEF2F2',
    borderWidth: 1,
    borderColor: '#FCA5A5',
    borderRadius: 8,
    padding: 10,
    marginBottom: 8,
    gap: 8,
  },
  errorText: {
    fontSize: 13,
    color: '#DC2626',
    fontWeight: '500',
    flex: 1,
  },
  loadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 4,
  },
  loadingText: {
    fontSize: 13,
    color: '#6B7280',
  },
  streamingIndicatorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 6,
  },
  streamingIndicatorText: {
    fontSize: 11,
    color: '#2563EB',
    fontWeight: '500',
  },
});
