import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  SafeAreaView,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppSettings, LiveModelId } from '../types';
import { LIVE_MODELS, AVAILABLE_VOICES } from '../constants/models';

interface SettingsModalProps {
  visible: boolean;
  settings: AppSettings;
  onClose: () => void;
  onSave: (updated: AppSettings) => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  visible,
  settings,
  onClose,
  onSave,
}) => {
  const [apiKey, setApiKey] = useState(settings.apiKey);
  const [selectedModel, setSelectedModel] = useState<LiveModelId>(settings.modelId);
  const [selectedVoice, setSelectedVoice] = useState(settings.voiceName);
  const [systemInstruction, setSystemInstruction] = useState(settings.systemInstruction);
  const [showApiKey, setShowApiKey] = useState(false);

  useEffect(() => {
    if (visible) {
      setApiKey(settings.apiKey);
      setSelectedModel(settings.modelId);
      setSelectedVoice(settings.voiceName);
      setSystemInstruction(settings.systemInstruction);
    }
  }, [visible, settings]);

  const handleSave = () => {
    onSave({
      apiKey: apiKey.trim(),
      modelId: selectedModel,
      voiceName: selectedVoice,
      systemInstruction: systemInstruction.trim(),
    });
    onClose();
  };

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onClose}
    >
      <SafeAreaView style={styles.safeArea}>
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          style={styles.keyboardView}
        >
          {/* Header */}
          <View style={styles.modalHeader}>
            <View style={styles.headerLeft}>
              <Ionicons name="settings" size={20} color="#1F2937" />
              <Text style={styles.headerTitle}>Live API Settings</Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeButton}>
              <Ionicons name="close" size={24} color="#4B5563" />
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.content} contentContainerStyle={styles.scrollContainer}>
            {/* API Key Section */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Gemini API Key</Text>
              <Text style={styles.sectionDescription}>
                Enter your Google AI Studio API key to wire up live WebSocket streaming.
              </Text>
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.textInput}
                  placeholder="AIzaSy..."
                  placeholderTextColor="#9CA3AF"
                  value={apiKey}
                  onChangeText={setApiKey}
                  secureTextEntry={!showApiKey}
                  autoCapitalize="none"
                  autoCorrect={false}
                />
                <TouchableOpacity
                  style={styles.visibilityToggle}
                  onPress={() => setShowApiKey(!showApiKey)}
                >
                  <Ionicons
                    name={showApiKey ? 'eye-off-outline' : 'eye-outline'}
                    size={20}
                    color="#6B7280"
                  />
                </TouchableOpacity>
              </View>
            </View>

            {/* Model Selection (Strictly -live- models) */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Live Model Selection</Text>
              <Text style={styles.sectionDescription}>
                Choose a low-latency -live- model for bidirectional audio and reasoning:
              </Text>

              <View style={styles.modelList}>
                {LIVE_MODELS.map((model) => {
                  const isSelected = selectedModel === model.id;
                  return (
                    <TouchableOpacity
                      key={model.id}
                      style={[styles.modelCard, isSelected && styles.modelCardSelected]}
                      onPress={() => setSelectedModel(model.id)}
                      activeOpacity={0.7}
                    >
                      <View style={styles.modelCardHeader}>
                        <View style={styles.modelCardRadio}>
                          {isSelected && <View style={styles.modelCardRadioInner} />}
                        </View>
                        <Text
                          style={[
                            styles.modelName,
                            isSelected && styles.modelNameSelected,
                          ]}
                        >
                          {model.name}
                        </Text>
                      </View>
                      <Text style={styles.modelDesc}>{model.description}</Text>
                      <Text style={styles.modelIdTag}>ID: {model.id}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* Voice Selection */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Voice Persona</Text>
              <View style={styles.voiceRow}>
                {AVAILABLE_VOICES.map((voice) => {
                  const isSelected = selectedVoice === voice;
                  return (
                    <TouchableOpacity
                      key={voice}
                      style={[styles.voiceChip, isSelected && styles.voiceChipSelected]}
                      onPress={() => setSelectedVoice(voice)}
                    >
                      <Ionicons
                        name="volume-medium-outline"
                        size={16}
                        color={isSelected ? '#FFFFFF' : '#374151'}
                      />
                      <Text
                        style={[
                          styles.voiceChipText,
                          isSelected && styles.voiceChipTextSelected,
                        ]}
                      >
                        {voice}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* System Instruction */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>System Instruction</Text>
              <TextInput
                style={[styles.textInput, styles.textArea]}
                value={systemInstruction}
                onChangeText={setSystemInstruction}
                multiline
                numberOfLines={3}
                placeholder="Instructions for the Live voice assistant..."
                placeholderTextColor="#9CA3AF"
              />
            </View>
          </ScrollView>

          {/* Footer Save Button */}
          <View style={styles.modalFooter}>
            <TouchableOpacity style={styles.cancelButton} onPress={onClose}>
              <Text style={styles.cancelButtonText}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.saveButton} onPress={handleSave}>
              <Text style={styles.saveButtonText}>Save Settings</Text>
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#F3F4F6',
  },
  keyboardView: {
    flex: 1,
  },
  modalHeader: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
    backgroundColor: '#FFFFFF',
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#111827',
  },
  closeButton: {
    padding: 4,
  },
  content: {
    flex: 1,
  },
  scrollContainer: {
    padding: 20,
    gap: 20,
  },
  section: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 4,
  },
  sectionDescription: {
    fontSize: 12,
    color: '#6B7280',
    marginBottom: 12,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 8,
    backgroundColor: '#F9FAFB',
  },
  textInput: {
    flex: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: '#111827',
  },
  textArea: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 8,
    backgroundColor: '#F9FAFB',
    minHeight: 80,
    textAlignVertical: 'top',
  },
  visibilityToggle: {
    paddingHorizontal: 12,
  },
  modelList: {
    gap: 10,
  },
  modelCard: {
    padding: 12,
    borderRadius: 8,
    borderWidth: 1.5,
    borderColor: '#E5E7EB',
    backgroundColor: '#F9FAFB',
  },
  modelCardSelected: {
    borderColor: '#2563EB',
    backgroundColor: '#EFF6FF',
  },
  modelCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
    gap: 8,
  },
  modelCardRadio: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: '#9CA3AF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  modelCardRadioInner: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#2563EB',
  },
  modelName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#374151',
  },
  modelNameSelected: {
    color: '#1D4ED8',
    fontWeight: '700',
  },
  modelDesc: {
    fontSize: 12,
    color: '#6B7280',
    marginLeft: 26,
    marginBottom: 4,
  },
  modelIdTag: {
    fontSize: 10,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    color: '#4B5563',
    marginLeft: 26,
    backgroundColor: '#E5E7EB',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    alignSelf: 'flex-start',
  },
  voiceRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  voiceChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#F3F4F6',
    borderWidth: 1,
    borderColor: '#D1D5DB',
  },
  voiceChipSelected: {
    backgroundColor: '#2563EB',
    borderColor: '#2563EB',
  },
  voiceChipText: {
    fontSize: 13,
    fontWeight: '500',
    color: '#374151',
  },
  voiceChipTextSelected: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  modalFooter: {
    flexDirection: 'row',
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    backgroundColor: '#FFFFFF',
    gap: 12,
  },
  cancelButton: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#D1D5DB',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#4B5563',
  },
  saveButton: {
    flex: 2,
    paddingVertical: 12,
    borderRadius: 8,
    backgroundColor: '#2563EB',
    alignItems: 'center',
    justifyContent: 'center',
  },
  saveButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#FFFFFF',
  },
});
