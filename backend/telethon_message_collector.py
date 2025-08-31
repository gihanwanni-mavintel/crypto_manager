import os
import asyncio
import json
from telethon import TelegramClient, events
from telethon.sessions import MemorySession
from dotenv import load_dotenv
import websockets
from websockets.exceptions import ConnectionClosed

# Load env
load_dotenv()
API_ID = int(os.getenv("API_ID"))
API_HASH = os.getenv("API_HASH")
GROUP_ID = int(os.getenv("GROUP_ID"))

# WebSocket clients
connected_clients = set()
MAX_CONNECTIONS = 100

def to_float_safe(value):
    if not value or value == "None" or value.lower() == "none":
        return None
    try:
        cleaned = ''.join(c for c in str(value) if c.isdigit() or c in ('.', '-'))
        if not cleaned or cleaned == '-':
            return None
        return float(cleaned)
    except (ValueError, TypeError):
        return None

def extract_value(label, lines):
    for line in lines:
        if label.lower() in line.lower():
            parts = line.split(":")
            if len(parts) > 1:
                value = parts[1].strip().replace("•", "").strip()
                if "stop loss" in label.lower() or "sl" in label.lower():
                    value = value.split('☠️')[0].strip()
                return value
    return None

async def websocket_handler(ws):
    if len(connected_clients) >= MAX_CONNECTIONS:
        await ws.close(code=1008, reason="Max connections reached")
        return
    connected_clients.add(ws)
    print(f"🌐 WebSocket client connected. Total: {len(connected_clients)}")
    try:
        async for msg in ws:
            print(f"Received from client: {msg}")
    except ConnectionClosed:
        pass
    finally:
        connected_clients.discard(ws)
        print(f"🌐 WebSocket client disconnected. Total: {len(connected_clients)}")

async def send_to_clients(data):
    if connected_clients:
        message = json.dumps(data, default=str)
        await asyncio.gather(*[client.send(message) for client in connected_clients], return_exceptions=True)

async def run_telegram_client():
    client = TelegramClient(MemorySession(), API_ID, API_HASH)

    await client.start()
    print("✅ Telegram client started and authenticated")

    @client.on(events.NewMessage(chats=GROUP_ID))
    async def handler(event):
        try:
            text = event.message.message
            date = event.message.date
            lines = [line.strip() for line in text.splitlines() if line.strip()]

            is_signal = (
                any(line.startswith('#') for line in lines) and
                any('entry' in line.lower() for line in lines) and
                any('profit' in line.lower() for line in lines) and
                any('loss' in line.lower() for line in lines)
            )

            if is_signal:
                first_line = lines[0] if lines else ""
                pair = first_line.split()[0].strip('#') if first_line else "UNKNOWN"
                setup_type = ("LONG" if "LONG" in first_line.upper()
                              else "SHORT" if "SHORT" in first_line.upper()
                              else "UNKNOWN")

                entry = extract_value("Entry", lines)
                leverage = extract_value("Leverage", lines)
                tp1 = extract_value("Target 1", lines) or extract_value("TP1", lines)
                tp2 = extract_value("Target 2", lines) or extract_value("TP2", lines)
                tp3 = extract_value("Target 3", lines) or extract_value("TP3", lines)
                tp4 = extract_value("Target 4", lines) or extract_value("TP4", lines)
                stop_loss = extract_value("Stop Loss", lines) or extract_value("SL", lines)

                await send_to_clients({
                    "type": "signal", "pair": pair, "setup_type": setup_type,
                    "entry": entry, "leverage": leverage,
                    "tp1": tp1, "tp2": tp2, "tp3": tp3, "tp4": tp4,
                    "stop_loss": stop_loss,
                    "timestamp": date.isoformat(),
                    "full_message": text
                })

                print(f"✅ Signal detected: {pair} {setup_type}")

            else:
                sender = event.message.sender.first_name if event.message.sender else "Unknown"

                await send_to_clients({
                    "type": "market", "sender": sender,
                    "text": text, "timestamp": date.isoformat()
                })

                print(f"📊 Market message: {text[:100]}...")

        except Exception as e:
            print(f"❌ Error processing message: {e}")
            import traceback
            traceback.print_exc()

    print("👂 Listening for Telegram messages...")
    await client.run_until_disconnected()

async def main():
    try:
        # Start WebSocket server
        server = await websockets.serve(
            websocket_handler, "0.0.0.0", int(os.environ.get("PORT", 6789)),
            ping_interval=20, ping_timeout=120, max_size=1000000
        )
        print(f"🌐 WebSocket server started on port {os.environ.get('PORT', 6789)}")

        # Start Telegram client
        await run_telegram_client()

    except Exception as e:
        print(f"💥 Fatal error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        print("🛑 Shutdown complete")

if __name__ == "__main__":
    asyncio.run(main())