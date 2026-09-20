import { File, Paths } from 'expo-file-system';

const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const lookup = new Uint8Array(256);
for (let i = 0; i < chars.length; i++) {
  lookup[chars.charCodeAt(i)] = i;
}

export function base64ToUint8Array(base64: string): Uint8Array {
  const clean = base64.replace(/[\r\n\t ]/g, '');
  let bufferLength = clean.length * 0.75;
  const len = clean.length;
  if (clean[len - 1] === '=') {
    bufferLength--;
    if (clean[len - 2] === '=') {
      bufferLength--;
    }
  }
  const bytes = new Uint8Array(bufferLength);
  let p = 0;
  for (let i = 0; i < len; i += 4) {
    const enc1 = lookup[clean.charCodeAt(i)];
    const enc2 = lookup[clean.charCodeAt(i + 1)];
    const enc3 = lookup[clean.charCodeAt(i + 2)];
    const enc4 = lookup[clean.charCodeAt(i + 3)];

    bytes[p++] = (enc1 << 2) | (enc2 >> 4);
    if (clean[i + 2] !== '=') {
      bytes[p++] = ((enc2 & 15) << 4) | (enc3 >> 2);
    }
    if (clean[i + 3] !== '=') {
      bytes[p++] = ((enc3 & 3) << 6) | (enc4 & 63);
    }
  }
  return bytes;
}

export function uint8ArrayToBase64(bytes: Uint8Array): string {
  let base64 = '';
  const len = bytes.length;
  for (let i = 0; i < len; i += 3) {
    const b0 = bytes[i];
    const b1 = i + 1 < len ? bytes[i + 1] : 0;
    const b2 = i + 2 < len ? bytes[i + 2] : 0;

    base64 += chars[b0 >> 2];
    base64 += chars[((b0 & 3) << 4) | (b1 >> 4)];
    base64 += i + 1 < len ? chars[((b1 & 15) << 2) | (b2 >> 6)] : '=';
    base64 += i + 2 < len ? chars[b2 & 63] : '=';
  }
  return base64;
}

export function createWavFromPcm(
  pcmBytes: Uint8Array,
  sampleRate = 24000,
  numChannels = 1,
  bitsPerSample = 16
): Uint8Array {
  const pcmLength = pcmBytes.length;
  const headerLength = 44;
  const wavBuffer = new ArrayBuffer(headerLength + pcmLength);
  const view = new DataView(wavBuffer);
  const bytes = new Uint8Array(wavBuffer);

  // 0-3: "RIFF"
  bytes.set([0x52, 0x49, 0x46, 0x46], 0);
  // 4-7: File size - 8
  view.setUint32(4, 36 + pcmLength, true);
  // 8-11: "WAVE"
  bytes.set([0x57, 0x41, 0x56, 0x45], 8);

  // 12-15: "fmt "
  bytes.set([0x66, 0x6d, 0x74, 0x20], 12);
  // 16-19: Subchunk1Size (16 for PCM)
  view.setUint32(16, 16, true);
  // 20-21: AudioFormat (1 for PCM)
  view.setUint16(20, 1, true);
  // 22-23: NumChannels
  view.setUint16(22, numChannels, true);
  // 24-27: SampleRate
  view.setUint32(24, sampleRate, true);
  // 28-31: ByteRate = SampleRate * NumChannels * BitsPerSample / 8
  view.setUint32(28, sampleRate * numChannels * (bitsPerSample / 8), true);
  // 32-33: BlockAlign = NumChannels * BitsPerSample / 8
  view.setUint16(32, numChannels * (bitsPerSample / 8), true);
  // 34-35: BitsPerSample
  view.setUint16(34, bitsPerSample, true);

  // 36-39: "data"
  bytes.set([0x64, 0x61, 0x74, 0x61], 36);
  // 40-43: Subchunk2Size (data size)
  view.setUint32(40, pcmLength, true);

  // 44+: PCM raw bytes
  bytes.set(pcmBytes, 44);

  return bytes;
}

export function pcmChunkToWavBase64(pcmBase64: string, sampleRate = 24000): string {
  const pcmBytes = base64ToUint8Array(pcmBase64);
  const wavBytes = createWavFromPcm(pcmBytes, sampleRate);
  return uint8ArrayToBase64(wavBytes);
}

export function combinePcmChunksToWavBytes(
  pcmChunksBase64: string[],
  sampleRate = 24000
): Uint8Array {
  const decoded = pcmChunksBase64.map(base64ToUint8Array);
  const totalLength = decoded.reduce((acc, c) => acc + c.length, 0);
  const combined = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of decoded) {
    combined.set(chunk, offset);
    offset += chunk.length;
  }
  return createWavFromPcm(combined, sampleRate);
}

export async function saveWavBytesToCache(
  wavBytes: Uint8Array,
  prefix = 'audio'
): Promise<string> {
  const filename = `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 8)}.wav`;
  const file = new File(Paths.cache, filename);
  file.write(wavBytes);
  return file.uri;
}
