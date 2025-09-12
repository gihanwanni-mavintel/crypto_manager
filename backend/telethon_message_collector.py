import os
import asyncio
import json
import logging
import re
from telethon import TelegramClient, events
from telethon.sessions import StringSession
from dotenv import load_dotenv
import websockets
from websockets.exceptions import ConnectionClosed
import asyncpg
from aiohttp import web
import aiohttp

async def http_handler(request):
    """Simple health check endpoint"""
    return web.Response(text="✅ Telegram Signal Bot is running")

# ----------------------------
# Logging
# ----------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    handlers=[logging.StreamHandler(), logging.FileHandler("app.log")]
)
logger = logging.getLogger(__name__)

# ----------------------------
# Environment
# ----------------------------
load_dotenv()
API_ID = int(os.getenv("API_ID", 0))
API_HASH = os.getenv("API_HASH", "")
GROUP_ID = int(os.getenv("GROUP_ID", 0))
SESSION_STRING = os.getenv("SESSION_STRING", "")
DATABASE_URL = os.getenv("DATABASE_URL", "")

if not API_ID or not API_HASH or not SESSION_STRING or not DATABASE_URL or not GROUP_ID:
    logger.error("❌ Missing critical environment variables. Exiting.")
    exit(1)

# ----------------------------
# WebSocket
# ----------------------------
connected_clients = set()
MAX_CONNECTIONS = 100

async def websocket_handler(ws):
    if len(connected_clients) >= MAX_CONNECTIONS:
        await ws.close(code=1008, reason="Max connections reached")
        return
    connected_clients.add(ws)
    logger.info(f"🌐 WebSocket client connected. Total: {len(connected_clients)}")
    try:
        async for _ in ws:
            pass
    except ConnectionClosed:
        pass
    finally:
        connected_clients.discard(ws)
        logger.info(f"🌐 WebSocket client disconnected. Total: {len(connected_clients)}")

async def send_to_clients(data):
    if connected_clients:
        message = json.dumps(data, default=str)
        dead_clients = set()
        for client in connected_clients:
            try:
                await client.send(message)
            except Exception:
                dead_clients.add(client)
        connected_clients.difference_update(dead_clients)

# ----------------------------
# Database
# ----------------------------
db_pool = None
signal_queue = asyncio.Queue()

async def init_db():
    global db_pool
    db_pool = await asyncpg.create_pool(dsn=DATABASE_URL)
    logger.info("✅ Connected to PostgreSQL")

    async with db_pool.acquire() as conn:
        await conn.execute("""
        CREATE TABLE IF NOT EXISTS signal_messages (
            id SERIAL PRIMARY KEY,
            pair TEXT,
            setup_type TEXT,
            entry TEXT,
            leverage TEXT,
            tp1 TEXT,
            tp2 TEXT,
            tp3 TEXT,
            tp4 TEXT,
            stop_loss TEXT,
            timestamp TIMESTAMPTZ,
            full_message TEXT UNIQUE
        )
        """)
        await conn.execute("""
        CREATE TABLE IF NOT EXISTS market_messages (
            id SERIAL PRIMARY KEY,
            sender TEXT,
            text TEXT,
            timestamp TIMESTAMPTZ
        )
        """)
    logger.info("✅ Tables ready")

async def signal_db_worker(batch_size=50):
    while True:
        batch = []
        try:
            msg = await signal_queue.get()
            batch.append(msg)
            while len(batch) < batch_size:
                try:
                    msg = signal_queue.get_nowait()
                    batch.append(msg)
                except asyncio.QueueEmpty:
                    break

            async with db_pool.acquire() as conn:
                # Avoid duplicates using ON CONFLICT
                values = [
                    (m["pair"], m["setup_type"], m["entry"], m["leverage"],
                     m["tp1"], m["tp2"], m["tp3"], m["tp4"], m["stop_loss"],
                     m["timestamp"], m["full_message"])
                    for m in batch
                ]
                if values:
                    await conn.executemany("""
                    INSERT INTO signal_messages (
                        pair, setup_type, entry, leverage,
                        tp1, tp2, tp3, tp4, stop_loss,
                        timestamp, full_message
                    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
                    ON CONFLICT (full_message) DO NOTHING
                    """, values)
                    logger.info(f"✅ Batch inserted {len(values)} signals")
        except Exception as e:
            logger.error(f"DB Worker error: {e}")

async def save_market(data):
    async with db_pool.acquire() as conn:
        await conn.execute("""
            INSERT INTO market_messages (sender, text, timestamp)
            VALUES ($1,$2,$3)
        """, data["sender"], data["text"], data["timestamp"])

# ----------------------------
# Telegram
# ----------------------------
def extract_value(label, lines):
    pattern = re.compile(rf"{label}\s*:\s*(.+)", re.IGNORECASE)
    for line in lines:
        match = pattern.search(line)
        if match:
            value = match.group(1).strip().replace("•", "").split("☠️")[0].strip()
            return value
    return None

async def run_telegram_client():
    client = TelegramClient(StringSession(SESSION_STRING), API_ID, API_HASH)
    await client.start()
    me = await client.get_me()
    logger.info(f"👤 Connected as: {me.first_name} (ID: {me.id})")

    @client.on(events.NewMessage(chats=GROUP_ID))
    async def handler(event):
        try:
            text = event.message.message
            date = event.message.date
            lines = [line.strip() for line in text.splitlines() if line.strip()]

            is_signal = (
                any(line.startswith('#') for line in lines) and
                any("entry" in line.lower() for line in lines) and
                any("profit" in line.lower() for line in lines) and
                any("loss" in line.lower() for line in lines)
            )

            if is_signal:
                first_line = lines[0] if lines else ""
                pair = first_line.split()[0].strip("#") if first_line else "UNKNOWN"
                setup_type = "LONG" if "LONG" in first_line.upper() else "SHORT" if "SHORT" in first_line.upper() else "UNKNOWN"

                entry = extract_value("Entry", lines)
                leverage = extract_value("Leverage", lines)
                tp1 = extract_value("Target 1", lines) or extract_value("TP1", lines)
                tp2 = extract_value("Target 2", lines) or extract_value("TP2", lines)
                tp3 = extract_value("Target 3", lines) or extract_value("TP3", lines)
                tp4 = extract_value("Target 4", lines) or extract_value("TP4", lines)
                stop_loss = extract_value("Stop Loss", lines) or extract_value("SL", lines)

                data = {
                    "pair": pair, "setup_type": setup_type,
                    "entry": entry, "leverage": leverage,
                    "tp1": tp1, "tp2": tp2, "tp3": tp3, "tp4": tp4,
                    "stop_loss": stop_loss,
                    "timestamp": date,
                    "full_message": text
                }

                await signal_queue.put(data)
                await send_to_clients(data)
                logger.info(f"✅ Signal queued: {pair} {setup_type}")
            else:
                sender = event.message.sender.first_name if event.message.sender else "Unknown"
                data = {"sender": sender, "text": text, "timestamp": date}
                await save_market(data)
                await send_to_clients(data)
                logger.info(f"📊 Market message saved: {text[:100]}...")
        except Exception as e:
            logger.error(f"❌ Error processing message: {e}")

    logger.info("👂 Listening for Telegram messages...")
    await client.run_until_disconnected()

# ----------------------------
# Main
# ----------------------------
async def main():
    await init_db()
    db_worker_task = asyncio.create_task(signal_db_worker())

    ws_port = int(os.getenv("PORT", 6789))

    app = web.Application()
        app.router.add_get('/', http_handler)
        app.router.add_get('/health', http_handler)
        runner = web.AppRunner(app)
        await runner.setup()
        site = web.TCPSite(runner, "0.0.0.0", ws_port)
        await site.start()
        logger.info(f"🌐 HTTP health server started on port {ws_port}")

    ws_server = await websockets.serve(websocket_handler, "0.0.0.0", ws_port)
    logger.info(f"🌐 WebSocket server started on port {ws_port}")

    try:
        await run_telegram_client()
    except Exception as e:
        logger.error(f"💥 Fatal error: {e}")
    finally:
        ws_server.close()
        await ws_server.wait_closed()
        await runner.cleanup()
        db_worker_task.cancel()
        await asyncio.sleep(0.1)
        logger.info("🛑 Shutdown complete")

if __name__ == "__main__":
    print("="*50)
    print("Telegram Signal Bot Starting...")
    print("="*50)
    asyncio.run(main())
