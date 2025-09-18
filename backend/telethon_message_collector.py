import os
import asyncio
import logging
from datetime import timezone

from telethon import TelegramClient, events
from telethon.sessions import StringSession
from dotenv import load_dotenv
import websockets
import asyncpg
from aiohttp import web

# ----------------------------
# Logging
# ----------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s"
)

# ----------------------------
# Load env
# ----------------------------
load_dotenv()
API_ID = int(os.getenv("API_ID"))
API_HASH = os.getenv("API_HASH")
SESSION_STRING = os.getenv("SESSION_STRING")
DB_URL = os.getenv("DATABASE_URL")
MARKET_CHAT = os.getenv("MARKET_CHAT", "MarketChatName")
SIGNAL_CHAT = os.getenv("SIGNAL_CHAT", "SignalChatName")
WS_PORT = int(os.getenv("WS_PORT", 8765))
HTTP_PORT = int(os.getenv("HTTP_PORT", 8080))

# ----------------------------
# Helper
# ----------------------------
def ensure_tz(dt):
    """Force datetime to be timezone-aware (UTC)."""
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)

# ----------------------------
# Postgres
# ----------------------------
async def init_db():
    conn = await asyncpg.connect(POSTGRES_URL)
    await conn.execute("""
        CREATE TABLE IF NOT EXISTS market_messages (
            id SERIAL PRIMARY KEY,
            sender TEXT,
            text TEXT,
            timestamp TIMESTAMPTZ,
            UNIQUE(text, timestamp)
        )
    """)
    await conn.execute("""
        CREATE TABLE IF NOT EXISTS signal_messages (
            id SERIAL PRIMARY KEY,
            sender TEXT,
            text TEXT,
            timestamp TIMESTAMPTZ,
            UNIQUE(text, timestamp)
        )
    """)
    await conn.close()
    logging.info("✅ Tables ready")

async def save_market(data):
    async with asyncpg.create_pool(POSTGRES_URL) as pool:
        async with pool.acquire() as conn:
            await conn.execute("""
                INSERT INTO market_messages (sender, text, timestamp)
                VALUES ($1,$2,$3)
                ON CONFLICT (text, timestamp) DO NOTHING
            """, data["sender"], data["text"], ensure_tz(data["timestamp"]))

async def save_signal(data):
    async with asyncpg.create_pool(POSTGRES_URL) as pool:
        async with pool.acquire() as conn:
            await conn.execute("""
                INSERT INTO signal_messages (sender, text, timestamp)
                VALUES ($1,$2,$3)
                ON CONFLICT (text, timestamp) DO NOTHING
            """, data["sender"], data["text"], ensure_tz(data["timestamp"]))

# ----------------------------
# Telegram
# ----------------------------
client = TelegramClient(StringSession(SESSION_STRING), API_ID, API_HASH)

@client.on(events.NewMessage(chats=[MARKET_CHAT]))
async def market_handler(event):
    sender = await event.get_sender()
    data = {
        "sender": sender.username or "Unknown",
        "text": event.message.message,
        "timestamp": ensure_tz(event.message.date)
    }
    await save_market(data)
    logging.info(f"💾 Market message saved: {data}")

@client.on(events.NewMessage(chats=[SIGNAL_CHAT]))
async def signal_handler(event):
    sender = await event.get_sender()
    data = {
        "sender": sender.username or "Unknown",
        "text": event.message.message,
        "timestamp": ensure_tz(event.message.date)
    }
    await save_signal(data)
    logging.info(f"💾 Signal message saved: {data}")

# ----------------------------
# Web server for Render health checks
# ----------------------------
async def handle_health(request):
    return web.Response(text="OK")

async def start_health_server():
    app = web.Application()
    app.router.add_get("/health", handle_health)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", HTTP_PORT)
    await site.start()
    logging.info(f"🌐 HTTP health server started on port {HTTP_PORT}")

# ----------------------------
# Main
# ----------------------------
async def main():
    try:
        conn = await asyncpg.connect(POSTGRES_URL)
        await conn.close()
        logging.info("✅ Connected to PostgreSQL")
    except Exception as e:
        logging.error(f"❌ Failed to connect to PostgreSQL: {e}")
        return

    await init_db()
    await start_health_server()
    await client.start()
    logging.info("🤖 Telegram client started")
    await client.run_until_disconnected()

if __name__ == "__main__":
    logging.info("==================================================")
    logging.info("Telegram Signal Bot Starting...")
    logging.info("==================================================")
    asyncio.run(main())
