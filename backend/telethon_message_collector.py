import os
import asyncio
import json
import logging
from telethon import TelegramClient, events
from telethon.sessions import StringSession
from dotenv import load_dotenv
import websockets
from websockets.exceptions import ConnectionClosed

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('app.log')
    ]
)
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()
API_ID = int(os.getenv("API_ID", 0))
API_HASH = os.getenv("API_HASH", "")
GROUP_ID = int(os.getenv("GROUP_ID", 0))
SESSION_STRING = os.getenv("SESSION_STRING", "")

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
    logger.info(f"🌐 WebSocket client connected. Total: {len(connected_clients)}")
    try:
        async for msg in ws:
            logger.debug(f"Received from client: {msg}")
    except ConnectionClosed:
        pass
    finally:
        connected_clients.discard(ws)
        logger.info(f"🌐 WebSocket client disconnected. Total: {len(connected_clients)}")

async def send_to_clients(data):
    if connected_clients:
        message = json.dumps(data, default=str)
        await asyncio.gather(*[client.send(message) for client in connected_clients], return_exceptions=True)

async def run_telegram_client():
    logger.info("🔄 Starting Telegram client...")

    # Debug environment variables
    logger.debug(f"API_ID: {API_ID}")
    logger.debug(f"API_HASH: {'*' * len(API_HASH) if API_HASH else 'MISSING'}")
    logger.debug(f"GROUP_ID: {GROUP_ID}")
    logger.debug(f"SESSION_STRING: {'SET' if SESSION_STRING else 'MISSING'}")

    if not SESSION_STRING:
        logger.error("❌ SESSION_STRING environment variable is missing!")
        return

    if not API_ID or not API_HASH:
        logger.error("❌ API_ID or API_HASH is missing!")
        return

    client = None
    try:
        logger.info("🔧 Creating Telegram client...")
        client = TelegramClient(
            session=StringSession(SESSION_STRING),
            api_id=API_ID,
            api_hash=API_HASH
        )

        logger.info("🚀 Starting client connection...")
        await client.start()
        logger.info("✅ Telegram client started successfully!")

        # Test connection
        try:
            me = await client.get_me()
            logger.info(f"👤 Connected as: {me.first_name} (ID: {me.id})")

            # Test group access
            try:
                entity = await client.get_entity(GROUP_ID)
                logger.info(f"📋 Group access: {entity.title}")
            except ValueError as e:
                logger.warning(f"⚠️ Group ID might be invalid: {e}")
            except Exception as e:
                logger.warning(f"⚠️ Group access issue: {e}")

        except Exception as e:
            logger.error(f"❌ Connection test failed: {e}")
            return

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

                    logger.info(f"✅ Signal detected: {pair} {setup_type}")

                else:
                    sender = event.message.sender.first_name if event.message.sender else "Unknown"

                    await send_to_clients({
                        "type": "market", "sender": sender,
                        "text": text, "timestamp": date.isoformat()
                    })

                    logger.info(f"📊 Market message: {text[:100]}...")

            except Exception as e:
                logger.error(f"❌ Error processing message: {e}")

        logger.info("👂 Listening for Telegram messages...")
        await client.run_until_disconnected()

    except asyncio.TimeoutError:
        logger.error("⏰ Telegram connection timeout - check network/firewall")
    except Exception as e:
        logger.error(f"💥 Telegram client error: {e}")
        import traceback
        logger.error(traceback.format_exc())
    finally:
        if client:
            await client.disconnect()
            logger.info("🔌 Client disconnected")

async def main():
    try:
        logger.info("🚀 Starting application...")

        # Check if we're on Render
        if 'RENDER' in os.environ:
            logger.info("🌐 Running on Render environment")
        else:
            logger.info("💻 Running locally")

        # Use WebSocket port
        ws_port = 6789

        logger.info(f"🌐 Starting WebSocket server on port {ws_port}")

        # Start WebSocket server
        server = await websockets.serve(
            websocket_handler, "0.0.0.0", ws_port,
            ping_interval=20, ping_timeout=120, max_size=1000000
        )
        logger.info(f"✅ WebSocket server started on port {ws_port}")

        # Start Telegram client
        await run_telegram_client()

    except Exception as e:
        logger.error(f"💥 Fatal error in main: {e}")
        import traceback
        logger.error(traceback.format_exc())
    finally:
        logger.info("🛑 Shutdown complete")

if __name__ == "__main__":
    # Print environment info
    print("=" * 50)
    print("Telegram Signal Bot Starting...")
    print("=" * 50)
    print(f"API_ID: {API_ID}")
    print(f"API_HASH: {'*' * len(API_HASH) if API_HASH else 'MISSING'}")
    print(f"GROUP_ID: {GROUP_ID}")
    print(f"SESSION_STRING: {'SET' if SESSION_STRING else 'MISSING'}")
    print("=" * 50)

    asyncio.run(main())