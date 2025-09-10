import os
import re
import asyncio
import logging
from decimal import Decimal, InvalidOperation
from telethon import TelegramClient, events
from dotenv import load_dotenv
import psycopg2
from psycopg2.extras import execute_values

# =========================
#  Load ENV variables
# =========================
load_dotenv()
API_ID = int(os.getenv("API_ID"))
API_HASH = os.getenv("API_HASH")
GROUP_ID = int(os.getenv("GROUP_ID"))
DB_URL = os.getenv("DATABASE_URL")

# =========================
#  Logging
# =========================
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger(__name__)

# =========================
#  Telethon Client
# =========================
client = TelegramClient("fetch_past_session", API_ID, API_HASH)

# =========================
#  Database Helper
# =========================
def get_db_connection():
    return psycopg2.connect(DB_URL)

async def init_db():
    """Create tables and indexes"""
    conn = get_db_connection()
    with conn.cursor() as cursor:
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS signal_messages (
            id SERIAL PRIMARY KEY,
            pair VARCHAR(50),
            setup_type VARCHAR(10),
            entry DECIMAL(18,8),
            leverage INTEGER,
            tp1 DECIMAL(18,8),
            tp2 DECIMAL(18,8),
            tp3 DECIMAL(18,8),
            tp4 DECIMAL(18,8),
            stop_loss DECIMAL(18,8),
            timestamp TIMESTAMP,
            full_message TEXT UNIQUE,
            CONSTRAINT unique_pair_time UNIQUE (pair, timestamp)
        );
        """)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS market_messages (
            id SERIAL PRIMARY KEY,
            sender VARCHAR(50),
            text TEXT UNIQUE,
            timestamp TIMESTAMP,
            CONSTRAINT unique_sender_time UNIQUE (sender, timestamp)
        );
        """)
        # Indexes
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_signal_timestamp ON signal_messages(timestamp);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_market_timestamp ON market_messages(timestamp);")
        conn.commit()
    conn.close()
    logger.info("✅ Database tables ready")

# =========================
#  Helpers
# =========================
def extract_decimal(value):
    if not value:
        return None
    try:
        cleaned = re.sub(r"[^\d.]+", "", value)
        return Decimal(cleaned)
    except (InvalidOperation, TypeError):
        return None

def extract_value(key, lines):
    for line in lines:
        if key.lower() in line.lower():
            parts = line.split(":", 1)
            if len(parts) == 2:
                return parts[1].strip()
    return None

# =========================
#  Save Functions
# =========================
async def save_signals(signals):
    if not signals:
        return
    conn = get_db_connection()
    with conn.cursor() as cursor:
        execute_values(cursor, """
            INSERT INTO signal_messages
            (pair, setup_type, entry, leverage, tp1, tp2, tp3, tp4, stop_loss, timestamp, full_message)
            VALUES %s
            ON CONFLICT DO NOTHING;
        """, [(
            s["pair"], s["setup_type"], s["entry"], s["leverage"],
            s["tp1"], s["tp2"], s["tp3"], s["tp4"], s["stop_loss"],
            s["timestamp"], s["full_message"]
        ) for s in signals])
        conn.commit()
    conn.close()
    logger.info(f"✅ Saved {len(signals)} signal messages")

async def save_markets(markets):
    if not markets:
        return
    conn = get_db_connection()
    with conn.cursor() as cursor:
        execute_values(cursor, """
            INSERT INTO market_messages
            (sender, text, timestamp)
            VALUES %s
            ON CONFLICT DO NOTHING;
        """, [(m["sender"], m["text"], m["timestamp"]) for m in markets])
        conn.commit()
    conn.close()
    logger.info(f"✅ Saved {len(markets)} market messages")

# =========================
#  Message Processing
# =========================
def parse_message(message):
    text = message.text.strip()
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    lower_lines = [line.lower() for line in lines]

    # Detect if it's a signal
    is_signal = (
        any(line.startswith('#') for line in lines) and
        any('entry' in line for line in lower_lines) and
        any('profit' in line for line in lower_lines) and
        any('loss' in line for line in lower_lines)
    )

    if is_signal:
        first_line = lines[0] if lines else ""
        pair = first_line.split()[0].strip('#') if first_line.startswith('#') else "UNKNOWN"
        setup_type = "LONG" if "long" in first_line.lower() else "SHORT" if "short" in first_line.lower() else "UNKNOWN"
        entry_raw = extract_value("Entry", lines)
        leverage_raw = extract_value("Leverage", lines)
        tp1_raw = extract_value("Target 1", lines) or extract_value("TP1", lines)
        tp2_raw = extract_value("Target 2", lines) or extract_value("TP2", lines)
        tp3_raw = extract_value("Target 3", lines) or extract_value("TP3", lines)
        tp4_raw = extract_value("Target 4", lines) or extract_value("TP4", lines)
        stop_loss_raw = extract_value("Stop Loss", lines) or extract_value("SL", lines)

        leverage = int(re.sub(r"[^\d]", "", leverage_raw)) if leverage_raw else None

        return "signal", {
            "pair": pair,
            "setup_type": setup_type,
            "entry": extract_decimal(entry_raw),
            "leverage": leverage,
            "tp1": extract_decimal(tp1_raw),
            "tp2": extract_decimal(tp2_raw),
            "tp3": extract_decimal(tp3_raw),
            "tp4": extract_decimal(tp4_raw),
            "stop_loss": extract_decimal(stop_loss_raw),
            "timestamp": message.date,
            "full_message": text
        }
    else:
        sender = "Unknown"
        return "market", {
            "sender": getattr(message.sender, 'first_name', None) or
                      getattr(message.sender, 'username', 'Unknown'),
            "text": text,
            "timestamp": message.date
        }

# =========================
#  Fetch Past Messages
# =========================
async def fetch_past_messages(limit=100):
    signals, markets = [], []
    async for message in client.iter_messages(GROUP_ID, limit=limit):
        if not message.text:
            continue
        msg_type, data = parse_message(message)
        if msg_type == "signal":
            signals.append(data)
        else:
            markets.append(data)
    await save_signals(signals)
    await save_markets(markets)
    logger.info(f"✅ Fetched past {limit} messages")

# =========================
#  Real-Time Handler
# =========================
@client.on(events.NewMessage(chats=GROUP_ID))
async def new_message_listener(event):
    msg_type, data =  parse_message(event.message)
    if msg_type == "signal":
        await save_signals([data])
    else:
        await save_markets([data])

# =========================
#  Main
# =========================
async def main():
    await init_db()
    await client.start()  # Connects the client
    logger.info("✅ Telegram client connected")
    await fetch_past_messages(limit=100)
    logger.info("⏳ Listening for new messages...")
    await client.run_until_disconnected()

if __name__ == "__main__":
    asyncio.run(main())
