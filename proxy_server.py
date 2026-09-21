import asyncio
import websockets
import json
import logging

logging.basicConfig(level=logging.INFO)

API_KEY = "AIzaSyBf78oerGJB5qpKFKCalUnTUFzfeiq5CT0"
GOOGLE_WS_URL = f"wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key={API_KEY}"

async def proxy_handler(client_ws):
    print("\n=====================================")
    print("[PROXY] New connection from Android app!")
    try:
        async with websockets.connect(GOOGLE_WS_URL) as google_ws:
            print("[PROXY] Connected to Google Gemini Live API")
            
            async def forward_to_google():
                try:
                    async for message in client_ws:
                        # Log message from App to Google
                        if isinstance(message, str):
                            print(f"\n[APP -> GOOGLE] (Text): {message[:500]}...")
                        else:
                            print(f"\n[APP -> GOOGLE] (Binary): {len(message)} bytes")
                        await google_ws.send(message)
                except websockets.exceptions.ConnectionClosed as e:
                    print(f"[PROXY] App closed connection: {e.code} {e.reason}")
                except Exception as e:
                    print(f"[PROXY] Error forwarding to Google: {e}")
                    
            async def forward_to_app():
                try:
                    async for message in google_ws:
                        if isinstance(message, str):
                            data = json.loads(message)
                            # Truncate audio bytes for cleaner logs
                            if 'serverContent' in data and 'modelTurn' in data['serverContent']:
                                parts = data['serverContent']['modelTurn'].get('parts', [])
                                for part in parts:
                                    if 'inlineData' in part:
                                        part['inlineData']['data'] = '<BASE64_AUDIO_TRUNCATED>'
                            print(f"\n[GOOGLE -> APP]: {json.dumps(data, indent=2)}")
                        else:
                            print(f"\n[GOOGLE -> APP] (Binary): {len(message)} bytes")
                        await client_ws.send(message)
                except websockets.exceptions.ConnectionClosed as e:
                    print(f"[PROXY] Google closed connection: {e.code} {e.reason}")
                except Exception as e:
                    print(f"[PROXY] Error forwarding to App: {e}")
                    
            await asyncio.gather(forward_to_google(), forward_to_app())
            
    except Exception as e:
        print("[PROXY] Failed to connect to Google:", e)

async def main():
    async with websockets.serve(proxy_handler, "0.0.0.0", 8000):
        print("[PROXY] Starting Proxy Server on ws://0.0.0.0:8000")
        print("[PROXY] Ensure Android app is connecting to ws://192.168.1.4:8000")
        await asyncio.Future()  # run forever

if __name__ == '__main__':
    asyncio.run(main())
