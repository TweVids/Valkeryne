import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Platform, StatusBar } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppSettings } from '../types';
import { LIVE_MODELS } from '../constants/models';

interface HeaderProps {
  settings: AppSettings;
  connectionStatus: 'disconnected' | 'connecting' | 'connected';
  onOpenSettings: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  settings,
  connectionStatus,
  onOpenSettings,
}) => {
  const currentModel =
    LIVE_MODELS.find((m) => m.id === settings.modelId)?.name || settings.modelId;

  return (
    <View style={styles.container}>
      {/* Top-left Settings Button */}
      <TouchableOpacity
        style={styles.settingsButton}
        onPress={onOpenSettings}
        accessibilityRole="button"
        accessibilityLabel="Open settings"
        activeOpacity={0.7}
      >
        <Ionicons name="settings-sharp" size={22} color="#1F2937" />
      </TouchableOpacity>

      {/* Title and Active Model */}
      <View style={styles.titleContainer}>
        <Text style={styles.titleText}>Valkeryne</Text>
        <View style={styles.modelBadge}>
          <View
            style={[
              styles.statusDot,
              connectionStatus === 'connected'
                ? styles.statusDotConnected
                : connectionStatus === 'connecting'
                ? styles.statusDotConnecting
                : styles.statusDotDisconnected,
            ]}
          />
          <Text style={styles.modelNameText} numberOfLines={1}>
            {currentModel}
          </Text>
        </View>
      </View>

      {/* Right placeholder to keep header balanced */}
      <View style={styles.rightPlaceholder} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight || 24) + 8 : 12,
    paddingBottom: 12,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#E5E7EB', // Neutral gray
    borderBottomWidth: 1,
    borderBottomColor: '#D1D5DB',
  },
  settingsButton: {
    width: 40,
    height: 40,
    borderRadius: 8,
    backgroundColor: '#F3F4F6',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#D1D5DB',
  },
  titleContainer: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  titleText: {
    fontSize: 18,
    fontWeight: '700',
    color: '#111827',
    letterSpacing: 0.5,
  },
  modelBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 2,
    backgroundColor: '#D1D5DB',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 12,
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    marginRight: 6,
  },
  statusDotConnected: {
    backgroundColor: '#10B981', // green
  },
  statusDotConnecting: {
    backgroundColor: '#F59E0B', // amber
  },
  statusDotDisconnected: {
    backgroundColor: '#9CA3AF', // gray
  },
  modelNameText: {
    fontSize: 11,
    color: '#374151',
    fontWeight: '600',
  },
  rightPlaceholder: {
    width: 40,
  },
});
