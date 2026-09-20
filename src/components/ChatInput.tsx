import React, { useState } from 'react';
import {
  View,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';

interface ChatInputProps {
  onSendMessage: (text: string) => void;
  disabled?: boolean;
  placeholder?: string;
}

export const ChatInput: React.FC<ChatInputProps> = ({
  onSendMessage,
  disabled = false,
  placeholder = 'Type a message to Gemini Live...',
}) => {
  const [text, setText] = useState('');

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed || disabled) return;
    onSendMessage(trimmed);
    setText('');
  };

  return (
    <View style={styles.container}>
      <View style={styles.inputRow}>
        {/* Small square box with small rounded corners */}
        <TextInput
          style={styles.inputBox}
          placeholder={placeholder}
          placeholderTextColor="#9CA3AF"
          value={text}
          onChangeText={setText}
          multiline={false}
          editable={!disabled}
          onSubmitEditing={handleSend}
          returnKeyType="send"
        />

        {/* Send Icon Button */}
        <TouchableOpacity
          style={[
            styles.sendButton,
            (!text.trim() || disabled) && styles.sendButtonDisabled,
          ]}
          onPress={handleSend}
          disabled={!text.trim() || disabled}
          accessibilityRole="button"
          accessibilityLabel="Send message"
          activeOpacity={0.7}
        >
          <Ionicons
            name="send"
            size={18}
            color={!text.trim() || disabled ? '#9CA3AF' : '#FFFFFF'}
          />
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#E5E7EB', // Gray background
    paddingHorizontal: 16,
    paddingTop: 10,
    paddingBottom: Platform.OS === 'ios' ? 24 : 12,
    borderTopWidth: 1,
    borderTopColor: '#D1D5DB',
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  inputBox: {
    flex: 1,
    height: 46,
    backgroundColor: '#FFFFFF',
    borderRadius: 8, // Small round on the edges
    borderWidth: 1,
    borderColor: '#D1D5DB',
    paddingHorizontal: 14,
    fontSize: 15,
    color: '#111827',
  },
  sendButton: {
    width: 46,
    height: 46,
    borderRadius: 8, // Matching square with small rounded edge
    backgroundColor: '#1F2937', // Dark sleek send button
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonDisabled: {
    backgroundColor: '#D1D5DB',
  },
});
